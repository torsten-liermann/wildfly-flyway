package com.github.wildfly.flyway.service;

import com.github.wildfly.flyway.config.FlywayConfigurationBuilder;
import com.github.wildfly.flyway.config.FlywayConfigurationBuilder.ConfigurationResult;
import com.github.wildfly.flyway.logging.FlywayLogger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service that executes Flyway migrations.
 * This service is only responsible for executing migrations - all configuration
 * logic is handled by FlywayDeploymentProcessor and FlywayConfigurationBuilder.
 */
public class FlywayMigrationService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;

    private final String deploymentName;
    private final Consumer<FlywayMigrationService> serviceConsumer;
    private final Supplier<DataSource> dataSourceSupplier;
    private final ClassLoader deploymentClassLoader;
    private final ConfigurationResult configuration;

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
                                  ClassLoader deploymentClassLoader,
                                  ConfigurationResult configuration) {
        this.deploymentName = deploymentName;
        this.serviceConsumer = serviceConsumer;
        this.dataSourceSupplier = dataSourceSupplier;
        this.deploymentClassLoader = deploymentClassLoader;
        this.configuration = configuration;
    }

    public void start(StartContext context) throws StartException {
        if (!started.compareAndSet(false, true)) {
            FlywayLogger.warnf("Flyway migration service already started for deployment: %s", deploymentName);
            return;
        }

        FlywayLogger.infof("Starting Flyway migration service for deployment: %s", deploymentName);

        lock.writeLock().lock();
        try {
            // Get datasource 
            DataSource dataSource = dataSourceSupplier.get();
            if (dataSource == null) {
                throw new StartException("DataSource is not available for deployment: " + deploymentName);
            }
            
            FlywayLogger.infof("DataSource obtained successfully for deployment: %s", deploymentName);

            // Test database connection with timeout
            testDatabaseConnection(dataSource);

            // Check if Flyway is enabled
            Map<String, String> properties = configuration.getFlywayProperties();
            String enabledValue = properties.get("spring.flyway.enabled");
            if (!"true".equalsIgnoreCase(enabledValue)) {
                FlywayLogger.infof("Flyway is disabled for deployment: %s", deploymentName);
                started.set(false); // Reset started flag
                return;
            }

            // Configure Flyway
            FluentConfiguration flywayConfig;
            
            // Use deployment classloader if available
            if (deploymentClassLoader != null) {
                flywayConfig = Flyway.configure(deploymentClassLoader);
                FlywayLogger.infof("Using deployment classloader for deployment: %s", deploymentName);
            } else {
                flywayConfig = Flyway.configure();
            }
            flywayConfig.connectRetries(MAX_RETRY_ATTEMPTS);
            
            // Apply configuration from ConfigurationResult
            FlywayConfigurationBuilder.applyToFlyway(flywayConfig, dataSource, deploymentClassLoader, properties);

            // Log configured locations
            String locationsProperty = properties.get("spring.flyway.locations");
            FlywayLogger.infof("Configured Flyway locations: %s",
                    locationsProperty != null ? locationsProperty : "default (classpath:db/migration)");

            // Load Flyway
            flyway = flywayConfig.load();

            // Execute migration
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

    private void testDatabaseConnection(DataSource dataSource) throws StartException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(CONNECTION_TIMEOUT_SECONDS)) {
                throw new StartException("Database connection is not valid");
            }
            
            // Log database info for debugging
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            String databaseVersion = connection.getMetaData().getDatabaseProductVersion();
            FlywayLogger.debugf("Connected to %s version %s", databaseProductName, databaseVersion);
            
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
            } else {
                FlywayLogger.infof("No pending migrations for deployment: %s", deploymentName);
            }

            // Execute migration
            lastMigrationResult = flyway.migrate();

            // Log results
            if (lastMigrationResult.success) {
                FlywayLogger.infof("Successfully executed %d migrations for deployment: %s",
                        lastMigrationResult.migrationsExecuted, deploymentName);

                // Log applied migrations
                if (lastMigrationResult.migrationsExecuted > 0) {
                    MigrationInfo[] applied = flyway.info().applied();
                    for (int i = applied.length - lastMigrationResult.migrationsExecuted; i < applied.length; i++) {
                        if (i >= 0 && i < applied.length) {
                            MigrationInfo info = applied[i];
                            FlywayLogger.infof("  Applied: %s - %s", info.getVersion(), info.getDescription());
                        }
                    }
                }

                // Cache migration info
                migrationCache.put(deploymentName, flyway.info().all());
            } else {
                FlywayLogger.errorf("Migration failed for deployment: %s", deploymentName);
                throw new StartException("Migration execution failed");
            }

        } catch (FlywayException e) {
            FlywayLogger.errorf(e, "Flyway migration error for deployment: %s", deploymentName);
            throw new StartException("Flyway migration failed", e);
        } finally {
            migrationInProgress.set(false);
        }
    }
}