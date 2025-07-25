package com.github.wildfly.flyway.service;

import com.github.wildfly.flyway.config.FlywayConfiguration;
import com.github.wildfly.flyway.config.SpringBootPropertyResolver;
import com.github.wildfly.flyway.logging.FlywayLogger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service that executes Flyway migrations - no CDI, pure MSC service.
 * Does not implement Service interface directly for WildFly 28 compatibility.
 * <p>
 * Thread-safe implementation with comprehensive error handling and validation.
 */
public class FlywayMigrationService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;

    private final String deploymentName;
    private final Consumer<FlywayMigrationService> serviceConsumer;
    private final Supplier<DataSource> dataSourceSupplier;
    private final ExpressionResolver expressionResolver;
    private final ClassLoader deploymentClassLoader;

    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean migrationInProgress = new AtomicBoolean(false);

    // Cache for migration results
    private final Map<String, MigrationInfo[]> migrationCache = new ConcurrentHashMap<>();

    private volatile Flyway flyway;
    private volatile MigrateResult lastMigrationResult;

    public FlywayMigrationService(String deploymentName,
                                  Consumer<FlywayMigrationService> serviceConsumer,
                                  Supplier<DataSource> dataSourceSupplier,
                                  Supplier<PathManager> pathManagerSupplier,
                                  ClassLoader deploymentClassLoader) {
        this.deploymentName = deploymentName;
        this.serviceConsumer = serviceConsumer;
        this.dataSourceSupplier = dataSourceSupplier;
        this.expressionResolver = ExpressionResolver.SIMPLE;
        this.deploymentClassLoader = deploymentClassLoader;
    }

    public void start(StartContext context) throws StartException {
        if (!started.compareAndSet(false, true)) {
            FlywayLogger.warnf("Flyway migration service already started for deployment: %s", deploymentName);
            return;
        }

        FlywayLogger.infof("Starting Flyway migration service for deployment: %s", deploymentName);

        lock.writeLock().lock();
        try {
            // Validate inputs
            validateConfiguration();

            DataSource dataSource = dataSourceSupplier.get();
            if (dataSource == null) {
                throw new StartException("DataSource is not available for deployment: " + deploymentName);
            }

            // Test database connection with timeout
            testDatabaseConnection(dataSource);

            // Detect vendor from datasource with retry logic
            String jdbcUrl = null;
            String vendor = null;

            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try (Connection connection = dataSource.getConnection()) {
                    jdbcUrl = connection.getMetaData().getURL();
                    vendor = SpringBootPropertyResolver.detectVendor(jdbcUrl);
                    FlywayLogger.debugf("Detected database vendor: %s from URL: %s", vendor, maskJdbcUrl(jdbcUrl));
                    break;
                } catch (SQLException e) {
                    if (attempt == MAX_RETRY_ATTEMPTS) {
                        FlywayLogger.errorf(e, "Failed to detect database vendor after %d attempts", MAX_RETRY_ATTEMPTS);
                        throw new StartException("Could not establish database connection", e);
                    }
                    FlywayLogger.warnf("Database connection attempt %d failed, retrying in %d ms: %s",
                            attempt, RETRY_DELAY_MS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new StartException("Interrupted while waiting for retry", ie);
                    }
                }
            }

            String detectedVendor = SpringBootPropertyResolver.detectVendor(jdbcUrl);

            // Resolve Spring Boot style properties with error handling
            Map<String, String> properties;
            try {
                // First, load deployment-specific properties from META-INF/flyway.properties
                Properties deploymentProps = loadDeploymentProperties();
                properties = new ConcurrentHashMap<>();
                
                if (!deploymentProps.isEmpty()) {
                    FlywayLogger.infof("Loaded %d properties from META-INF/flyway.properties", deploymentProps.size());
                    // Process deployment properties first, normalizing keys
                    for (String key : deploymentProps.stringPropertyNames()) {
                        String value = deploymentProps.getProperty(key);
                        // Normalize key to spring.flyway.* format
                        String normalizedKey = normalizePropertyKey(key);
                        FlywayLogger.infof("Property normalization: '%s' -> '%s' = '%s'", key, normalizedKey, value);
                        // Replace {vendor} placeholders
                        if (value != null && detectedVendor != null && value.contains("{vendor}")) {
                            value = value.replace("{vendor}", detectedVendor);
                            FlywayLogger.debugf("Replaced vendor placeholder: '%s' -> '%s'", deploymentProps.getProperty(key), value);
                        }
                        properties.put(normalizedKey, value);
                    }
                }
                
                // Then resolve system properties and environment variables
                SpringBootPropertyResolver resolver = new SpringBootPropertyResolver(
                        expressionResolver,
                        detectedVendor);
                Map<String, String> systemProperties = resolver.resolveProperties();
                
                // Merge system properties, but deployment properties take precedence
                final Map<String, String> finalProperties = properties;
                systemProperties.forEach((key, value) -> {
                    if (!finalProperties.containsKey(key)) {
                        finalProperties.put(key, value);
                    }
                });

                if (properties.isEmpty()) {
                    FlywayLogger.warnf("No Flyway properties found for deployment: %s, using defaults", deploymentName);
                    properties = getDefaultProperties();
                }
            } catch (Exception e) {
                FlywayLogger.errorf(e, "Error resolving properties for deployment: %s", deploymentName);
                throw new StartException("Failed to resolve Flyway properties", e);
            }

            // Check if Flyway is enabled
            FlywayLogger.infof("Checking enabled status. Properties contain: %s", properties.keySet());
            String enabledValue = properties.get("spring.flyway.enabled");
            FlywayLogger.infof("spring.flyway.enabled = '%s'", enabledValue);
            if (!"true".equalsIgnoreCase(enabledValue)) {
                FlywayLogger.infof("Flyway is disabled for deployment: %s", deploymentName);
                started.set(false); // Reset started flag
                return;
            }

            // Configure Flyway using comprehensive configuration
            FluentConfiguration config;
            try {
                // Use deployment classloader if available, otherwise use default
                if (deploymentClassLoader != null) {
                    config = Flyway.configure(deploymentClassLoader)
                            .dataSource(dataSource)
                            .connectRetries(MAX_RETRY_ATTEMPTS);
                    FlywayLogger.infof("Using deployment classloader for deployment: %s", deploymentName);

                    // Debug: Check if migrations are visible
                    try {
                        var resource = deploymentClassLoader.getResource("db/migration/V1__Create_person_table.sql");
                        if (resource != null) {
                            FlywayLogger.infof("Found migration resource: %s", resource);
                        } else {
                            FlywayLogger.warnf("Migration resource NOT found in deployment classloader");
                            // Try other locations
                            String[] testLocations = {
                                    "WEB-INF/classes/db/migration/V1__Create_person_table.sql",
                                    "META-INF/db/migration/V1__Create_person_table.sql",
                                    "/db/migration/V1__Create_person_table.sql"
                            };
                            for (String loc : testLocations) {
                                var testResource = deploymentClassLoader.getResource(loc);
                                if (testResource != null) {
                                    FlywayLogger.infof("Found migration at alternate location: %s -> %s", loc, testResource);
                                }
                            }
                        }
                    } catch (Exception e) {
                        FlywayLogger.debugf("Error checking for migration resources: %s", e.getMessage());
                    }
                } else {
                    config = Flyway.configure()
                            .dataSource(dataSource)
                            .connectRetries(MAX_RETRY_ATTEMPTS);
                }

                // Apply comprehensive configuration
                FlywayConfiguration flywayConfig = new FlywayConfiguration(properties);
                flywayConfig.applyTo(config);

                FlywayLogger.infof("Applied comprehensive Flyway configuration for deployment: %s", deploymentName);

                // Debug: Log configured locations
                String locationsProperty = properties.get("spring.flyway.locations");
                FlywayLogger.infof("Configured Flyway locations: %s",
                        locationsProperty != null ? locationsProperty : "default (classpath:db/migration)");

            } catch (Exception e) {
                FlywayLogger.errorf(e, "Error configuring Flyway for deployment: %s", deploymentName);
                throw new StartException("Failed to configure Flyway", e);
            }

            flyway = config.load();

            // Execute migration with monitoring
            executeMigration();

            serviceConsumer.accept(this);

        } catch (StartException e) {
            started.set(false); // Reset on failure
            throw e;
        } catch (FlywayException e) {
            started.set(false); // Reset on failure
            FlywayLogger.errorf(e, "Flyway migration failed for deployment: %s", deploymentName);
            throw new StartException("Flyway migration failed for deployment: " + deploymentName, e);
        } catch (Exception e) {
            started.set(false); // Reset on failure
            FlywayLogger.errorf(e, "Unexpected error during Flyway migration for deployment: %s", deploymentName);
            throw new StartException("Unexpected error during Flyway migration", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void stop(StopContext context) {
        if (!started.compareAndSet(true, false)) {
            FlywayLogger.debugf("Flyway migration service already stopped for deployment: %s", deploymentName);
            return;
        }

        lock.writeLock().lock();
        try {
            FlywayLogger.infof("Stopping Flyway migration service for deployment: %s", deploymentName);

            // Wait for any ongoing migration to complete
            int waitCount = 0;
            while (migrationInProgress.get() && waitCount < 30) {
                FlywayLogger.debugf("Waiting for migration to complete before stopping...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                waitCount++;
            }

            if (migrationInProgress.get()) {
                FlywayLogger.warnf("Migration still in progress after 30 seconds, forcing stop");
            }

            // Clear resources
            flyway = null;
            lastMigrationResult = null;
            migrationCache.clear();

            serviceConsumer.accept(null);

        } finally {
            lock.writeLock().unlock();
        }
    }

    // applyAdditionalProperties method removed - now handled by FlywayConfiguration class

    public Flyway getFlyway() {
        lock.readLock().lock();
        try {
            return flyway;
        } finally {
            lock.readLock().unlock();
        }
    }

    public MigrateResult getLastMigrationResult() {
        lock.readLock().lock();
        try {
            return lastMigrationResult;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isMigrationInProgress() {
        return migrationInProgress.get();
    }

    // ===== Private Helper Methods =====

    private void validateConfiguration() throws StartException {
        if (deploymentName == null || deploymentName.trim().isEmpty()) {
            throw new StartException("Deployment name cannot be null or empty");
        }

        if (serviceConsumer == null) {
            throw new StartException("Service consumer cannot be null");
        }

        if (dataSourceSupplier == null) {
            throw new StartException("DataSource supplier cannot be null");
        }

        if (expressionResolver == null) {
            throw new StartException("Expression resolver cannot be null");
        }
    }

    private void testDatabaseConnection(DataSource dataSource) throws StartException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(CONNECTION_TIMEOUT_SECONDS)) {
                throw new StartException("Database connection is not valid");
            }
        } catch (SQLException e) {
            throw new StartException("Failed to test database connection", e);
        }
    }

    private void executeMigration() throws StartException {
        if (!migrationInProgress.compareAndSet(false, true)) {
            throw new StartException("Migration already in progress for deployment: " + deploymentName);
        }

        try {
            FlywayLogger.infof("Starting database migration for deployment: %s", deploymentName);

            // Log current migration status
            MigrationInfo[] pending = flyway.info().pending();
            if (pending.length > 0) {
                FlywayLogger.infof("Found %d pending migrations for deployment: %s", pending.length, deploymentName);
                for (MigrationInfo info : pending) {
                    FlywayLogger.debugf("  Pending: %s - %s", info.getVersion(), info.getDescription());
                }
            }

            // Execute migration
            lastMigrationResult = flyway.migrate();

            // Log results
            if (lastMigrationResult.success) {
                FlywayLogger.infof("Successfully executed %d migrations for deployment: %s",
                        lastMigrationResult.migrationsExecuted, deploymentName);

                // Cache migration info
                migrationCache.put(deploymentName, flyway.info().all());
            } else {
                FlywayLogger.errorf("Migration failed for deployment: %s", deploymentName);
                throw new StartException("Migration execution failed");
            }

        } finally {
            migrationInProgress.set(false);
        }
    }

    /**
     * Load properties from META-INF/flyway.properties in the deployment
     */
    private Properties loadDeploymentProperties() {
        Properties properties = new Properties();
        if (deploymentClassLoader != null) {
            try (InputStream is = deploymentClassLoader.getResourceAsStream("META-INF/flyway.properties")) {
                if (is != null) {
                    properties.load(is);
                    FlywayLogger.debugf("Loaded deployment properties from META-INF/flyway.properties");
                }
            } catch (IOException e) {
                FlywayLogger.warnf("Failed to load META-INF/flyway.properties: %s", e.getMessage());
            }
        }
        return properties;
    }
    
    /**
     * Normalize property key to spring.flyway.* format.
     */
    private String normalizePropertyKey(String key) {
        if (key == null) {
            return null;
        }
        
        // If it already starts with spring.flyway., return as is
        if (key.startsWith("spring.flyway.")) {
            return key;
        }
        
        // If it starts with flyway., convert to spring.flyway.
        if (key.startsWith("flyway.")) {
            return "spring.flyway." + key.substring("flyway.".length());
        }
        
        // Otherwise return as is (shouldn't happen with our checks)
        return key;
    }

    private Map<String, String> getDefaultProperties() {
        Map<String, String> defaults = new ConcurrentHashMap<>();
        defaults.put("spring.flyway.enabled", "true");
        defaults.put("spring.flyway.locations", "classpath:db/migration");
        defaults.put("spring.flyway.baseline-on-migrate", "false");
        defaults.put("spring.flyway.clean-disabled", "true");
        defaults.put("spring.flyway.validate-on-migrate", "true");
        defaults.put("spring.flyway.sql-migration-prefix", "V");
        defaults.put("spring.flyway.sql-migration-separator", "__");
        defaults.put("spring.flyway.repeatable-sql-migration-prefix", "R");
        return defaults;
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String getOrDefault(Map<String, String> properties, String key, String defaultValue) {
        String value = properties.get(key);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }

    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        // Mask password in JDBC URL
        return jdbcUrl.replaceAll("password=[^;&]*", "password=***")
                .replaceAll(":[^:@]+@", ":***@"); // user:password@host pattern
    }
}
