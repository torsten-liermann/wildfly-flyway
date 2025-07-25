package com.github.wildfly.flyway.config;

import com.github.wildfly.flyway.logging.FlywayLogger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.pattern.ValidatePattern;

/**
 * Comprehensive Flyway configuration supporting all Spring Boot properties.
 * Thread-safe implementation with validation and defaults.
 */
public class FlywayConfiguration {
    
    // Configuration keys
    public static final String PREFIX = "spring.flyway.";
    
    // Core properties
    public static final String ENABLED = PREFIX + "enabled";
    public static final String LOCATIONS = PREFIX + "locations";
    public static final String SCHEMAS = PREFIX + "schemas";
    public static final String TABLE = PREFIX + "table";
    public static final String TABLESPACE = PREFIX + "tablespace";
    public static final String TARGET = PREFIX + "target";
    public static final String CHERRY_PICK = PREFIX + "cherry-pick";
    
    // Baseline properties
    public static final String BASELINE_ON_MIGRATE = PREFIX + "baseline-on-migrate";
    public static final String BASELINE_VERSION = PREFIX + "baseline-version";
    public static final String BASELINE_DESCRIPTION = PREFIX + "baseline-description";
    
    // Validation properties
    public static final String VALIDATE_ON_MIGRATE = PREFIX + "validate-on-migrate";
    public static final String VALIDATE_MIGRATION_NAMING = PREFIX + "validate-migration-naming";
    public static final String IGNORE_MIGRATION_PATTERNS = PREFIX + "ignore-migration-patterns";
    
    // Migration properties
    public static final String SQL_MIGRATION_PREFIX = PREFIX + "sql-migration-prefix";
    public static final String SQL_MIGRATION_SUFFIXES = PREFIX + "sql-migration-suffixes";
    public static final String SQL_MIGRATION_SEPARATOR = PREFIX + "sql-migration-separator";
    public static final String REPEATABLE_SQL_MIGRATION_PREFIX = PREFIX + "repeatable-sql-migration-prefix";
    
    // Clean properties
    public static final String CLEAN_DISABLED = PREFIX + "clean-disabled";
    public static final String CLEAN_ON_VALIDATION_ERROR = PREFIX + "clean-on-validation-error";
    
    // Advanced properties
    public static final String OUT_OF_ORDER = PREFIX + "out-of-order";
    public static final String SKIP_DEFAULT_CALLBACKS = PREFIX + "skip-default-callbacks";
    public static final String SKIP_DEFAULT_RESOLVERS = PREFIX + "skip-default-resolvers";
    public static final String MIXED = PREFIX + "mixed";
    public static final String GROUP = PREFIX + "group";
    public static final String INSTALLED_BY = PREFIX + "installed-by";
    public static final String CREATE_SCHEMAS = PREFIX + "create-schemas";
    public static final String ENCODING = PREFIX + "encoding";
    public static final String DETECT_ENCODING = PREFIX + "detect-encoding";
    public static final String LOCK_RETRY_COUNT = PREFIX + "lock-retry-count";
    public static final String CONNECT_RETRIES = PREFIX + "connect-retries";
    public static final String CONNECT_RETRIES_INTERVAL = PREFIX + "connect-retries-interval";
    
    // Database-specific properties
    public static final String ORACLE_SQLPLUS = PREFIX + "oracle-sqlplus";
    public static final String ORACLE_SQLPLUS_WARN = PREFIX + "oracle-sqlplus-warn";
    public static final String ORACLE_KERBEROS_CACHE_FILE = PREFIX + "oracle-kerberos-cache-file";
    public static final String ORACLE_WALLET_LOCATION = PREFIX + "oracle-wallet-location";
    
    // PostgreSQL-specific
    public static final String POSTGRESQL_TRANSACTIONAL_LOCK = PREFIX + "postgresql-transactional-lock";
    
    // Script properties
    public static final String SCRIPT_PLACEHOLDER_PREFIX = PREFIX + "script-placeholder-prefix";
    public static final String SCRIPT_PLACEHOLDER_SUFFIX = PREFIX + "script-placeholder-suffix";
    
    // Placeholder properties
    public static final String PLACEHOLDER_REPLACEMENT = PREFIX + "placeholder-replacement";
    public static final String PLACEHOLDER_PREFIX = PREFIX + "placeholder-prefix";
    public static final String PLACEHOLDER_SUFFIX = PREFIX + "placeholder-suffix";
    public static final String PLACEHOLDER_SEPARATOR = PREFIX + "placeholder-separator";
    public static final String PLACEHOLDERS = PREFIX + "placeholders.";
    
    // Callback properties
    public static final String CALLBACKS = PREFIX + "callbacks";
    
    // Resolver properties
    public static final String RESOLVERS = PREFIX + "resolvers";
    
    // JDBC properties
    public static final String JDBC_PROPERTIES = PREFIX + "jdbc-properties.";
    
    // Defaults
    private static final Map<String, String> DEFAULTS = new ConcurrentHashMap<>();
    
    static {
        // Initialize defaults
        DEFAULTS.put(ENABLED, "true");
        DEFAULTS.put(LOCATIONS, "classpath:db/migration");
        DEFAULTS.put(TABLE, "flyway_schema_history");
        DEFAULTS.put(BASELINE_ON_MIGRATE, "false");
        DEFAULTS.put(BASELINE_VERSION, "1");
        DEFAULTS.put(BASELINE_DESCRIPTION, "<< Flyway Baseline >>");
        DEFAULTS.put(VALIDATE_ON_MIGRATE, "true");
        DEFAULTS.put(VALIDATE_MIGRATION_NAMING, "false");
        DEFAULTS.put(CLEAN_DISABLED, "true");
        DEFAULTS.put(CLEAN_ON_VALIDATION_ERROR, "false");
        DEFAULTS.put(SQL_MIGRATION_PREFIX, "V");
        DEFAULTS.put(SQL_MIGRATION_SUFFIXES, ".sql");
        DEFAULTS.put(SQL_MIGRATION_SEPARATOR, "__");
        DEFAULTS.put(REPEATABLE_SQL_MIGRATION_PREFIX, "R");
        DEFAULTS.put(OUT_OF_ORDER, "false");
        DEFAULTS.put(SKIP_DEFAULT_CALLBACKS, "false");
        DEFAULTS.put(SKIP_DEFAULT_RESOLVERS, "false");
        DEFAULTS.put(MIXED, "false");
        DEFAULTS.put(GROUP, "false");
        DEFAULTS.put(CREATE_SCHEMAS, "true");
        DEFAULTS.put(ENCODING, StandardCharsets.UTF_8.name());
        DEFAULTS.put(DETECT_ENCODING, "false");
        DEFAULTS.put(PLACEHOLDER_REPLACEMENT, "true");
        DEFAULTS.put(PLACEHOLDER_PREFIX, "${");
        DEFAULTS.put(PLACEHOLDER_SUFFIX, "}");
        DEFAULTS.put(PLACEHOLDER_SEPARATOR, ":");
        DEFAULTS.put(SCRIPT_PLACEHOLDER_PREFIX, "FP__");
        DEFAULTS.put(SCRIPT_PLACEHOLDER_SUFFIX, "__");
        DEFAULTS.put(LOCK_RETRY_COUNT, "50");
        DEFAULTS.put(CONNECT_RETRIES, "0");
        DEFAULTS.put(CONNECT_RETRIES_INTERVAL, "120");
        DEFAULTS.put(ORACLE_SQLPLUS, "false");
        DEFAULTS.put(ORACLE_SQLPLUS_WARN, "false");
        DEFAULTS.put(POSTGRESQL_TRANSACTIONAL_LOCK, "true");
    }
    
    private final Map<String, String> properties;
    
    public FlywayConfiguration(Map<String, String> properties) {
        this.properties = new ConcurrentHashMap<>(properties);
        applyDefaults();
    }
    
    /**
     * Apply configuration to Flyway FluentConfiguration.
     */
    public void applyTo(FluentConfiguration config) {
        FlywayLogger.debug("Applying Flyway configuration");
        long startTime = FlywayLogger.startOperation("config.apply");
        
        try {
            // Core configuration
            applyLocations(config);
            applySchemas(config);
            applyTable(config);
            applyTarget(config);
            applyCherryPick(config);
            
            // Baseline configuration
            applyBaseline(config);
            
            // Validation configuration
            applyValidation(config);
            
            // Migration configuration
            applyMigration(config);
            
            // Clean configuration
            applyClean(config);
            
            // Advanced configuration
            applyAdvanced(config);
            
            // Database-specific configuration
            applyDatabaseSpecific(config);
            
            // Placeholder configuration
            applyPlaceholders(config);
            
            // Callback and resolver configuration
            applyCallbacksAndResolvers(config);
            
            // JDBC properties
            applyJdbcProperties(config);
            
            FlywayLogger.logConfig(org.jboss.logging.Logger.Level.INFO, 
                "Applied %d configuration properties", properties.size());
                
        } finally {
            FlywayLogger.endOperation("config.apply", startTime);
        }
    }
    
    private void applyDefaults() {
        DEFAULTS.forEach(properties::putIfAbsent);
    }
    
    private void applyLocations(FluentConfiguration config) {
        String locations = getProperty(LOCATIONS);
        if (locations != null && !locations.trim().isEmpty()) {
            String[] locationArray = locations.split(",");
            for (int i = 0; i < locationArray.length; i++) {
                locationArray[i] = locationArray[i].trim();
            }
            config.locations(locationArray);
            FlywayLogger.debugf("Configured locations: %s", locations);
        }
    }
    
    private void applySchemas(FluentConfiguration config) {
        String schemas = getProperty(SCHEMAS);
        if (schemas != null && !schemas.trim().isEmpty()) {
            config.schemas(schemas.split(","));
            FlywayLogger.debugf("Configured schemas: %s", schemas);
        }
    }
    
    private void applyTable(FluentConfiguration config) {
        String table = getProperty(TABLE);
        if (table != null && !table.trim().isEmpty()) {
            config.table(table);
            FlywayLogger.debugf("Configured table: %s", table);
        }
        
        String tablespace = getProperty(TABLESPACE);
        if (tablespace != null && !tablespace.trim().isEmpty()) {
            config.tablespace(tablespace);
            FlywayLogger.debugf("Configured tablespace: %s", tablespace);
        }
    }
    
    private void applyTarget(FluentConfiguration config) {
        String target = getProperty(TARGET);
        if (target != null && !target.trim().isEmpty()) {
            if ("latest".equalsIgnoreCase(target)) {
                config.target(MigrationVersion.LATEST);
            } else {
                config.target(MigrationVersion.fromVersion(target));
            }
            FlywayLogger.debugf("Configured target: %s", target);
        }
    }
    
    private void applyCherryPick(FluentConfiguration config) {
        String cherryPick = getProperty(CHERRY_PICK);
        if (cherryPick != null && !cherryPick.trim().isEmpty()) {
            // Flyway 11 removed cherryPick feature - log warning
            FlywayLogger.warnf("Cherry-pick feature is not available in Flyway 11. Ignoring configuration: %s", cherryPick);
            // In Flyway 11, you need to use target version and skip specific versions
        }
    }
    
    private void applyBaseline(FluentConfiguration config) {
        config.baselineOnMigrate(getBoolean(BASELINE_ON_MIGRATE));
        
        String baselineVersion = getProperty(BASELINE_VERSION);
        if (baselineVersion != null) {
            config.baselineVersion(baselineVersion);
        }
        
        String baselineDescription = getProperty(BASELINE_DESCRIPTION);
        if (baselineDescription != null) {
            config.baselineDescription(baselineDescription);
        }
        
        FlywayLogger.debugf("Configured baseline: onMigrate=%s, version=%s", 
            getBoolean(BASELINE_ON_MIGRATE), baselineVersion);
    }
    
    private void applyValidation(FluentConfiguration config) {
        config.validateOnMigrate(getBoolean(VALIDATE_ON_MIGRATE));
        config.validateMigrationNaming(getBoolean(VALIDATE_MIGRATION_NAMING));
        
        String ignorePatterns = getProperty(IGNORE_MIGRATION_PATTERNS);
        if (ignorePatterns != null && !ignorePatterns.trim().isEmpty()) {
            String[] patterns = ignorePatterns.split(",");
            ValidatePattern[] validatePatterns = new ValidatePattern[patterns.length];
            for (int i = 0; i < patterns.length; i++) {
                String pattern = patterns[i].trim();
                if (pattern.startsWith("*:")) {
                    validatePatterns[i] = ValidatePattern.fromPattern(pattern);
                } else {
                    validatePatterns[i] = ValidatePattern.fromPattern("*:" + pattern);
                }
            }
            config.ignoreMigrationPatterns(validatePatterns);
            FlywayLogger.debugf("Configured ignore patterns: %s", ignorePatterns);
        }
    }
    
    private void applyMigration(FluentConfiguration config) {
        config.sqlMigrationPrefix(getProperty(SQL_MIGRATION_PREFIX));
        config.sqlMigrationSeparator(getProperty(SQL_MIGRATION_SEPARATOR));
        config.repeatableSqlMigrationPrefix(getProperty(REPEATABLE_SQL_MIGRATION_PREFIX));
        
        String suffixes = getProperty(SQL_MIGRATION_SUFFIXES);
        if (suffixes != null) {
            config.sqlMigrationSuffixes(suffixes.split(","));
        }
        
        FlywayLogger.debugf("Configured migration: prefix=%s, separator=%s, suffixes=%s",
            getProperty(SQL_MIGRATION_PREFIX), getProperty(SQL_MIGRATION_SEPARATOR), suffixes);
    }
    
    private void applyClean(FluentConfiguration config) {
        boolean cleanDisabled = getBoolean(CLEAN_DISABLED);
        config.cleanDisabled(cleanDisabled);
        
        if (cleanDisabled) {
            FlywayLogger.logSecurity(org.jboss.logging.Logger.Level.INFO, 
                "Clean operations are disabled for safety");
        } else {
            FlywayLogger.logSecurity(org.jboss.logging.Logger.Level.WARN, 
                "Clean operations are ENABLED - use with caution!");
        }
        
        config.cleanOnValidationError(getBoolean(CLEAN_ON_VALIDATION_ERROR));
    }
    
    private void applyAdvanced(FluentConfiguration config) {
        config.outOfOrder(getBoolean(OUT_OF_ORDER));
        config.skipDefaultCallbacks(getBoolean(SKIP_DEFAULT_CALLBACKS));
        config.skipDefaultResolvers(getBoolean(SKIP_DEFAULT_RESOLVERS));
        config.mixed(getBoolean(MIXED));
        config.group(getBoolean(GROUP));
        config.createSchemas(getBoolean(CREATE_SCHEMAS));
        
        String installedBy = getProperty(INSTALLED_BY);
        if (installedBy != null && !installedBy.trim().isEmpty()) {
            config.installedBy(installedBy);
        }
        
        String encoding = getProperty(ENCODING);
        if (encoding != null && !encoding.trim().isEmpty()) {
            config.encoding(Charset.forName(encoding));
        }
        
        config.detectEncoding(getBoolean(DETECT_ENCODING));
        config.lockRetryCount(getInt(LOCK_RETRY_COUNT, 50));
        config.connectRetries(getInt(CONNECT_RETRIES, 0));
        config.connectRetriesInterval(getInt(CONNECT_RETRIES_INTERVAL, 120));
    }
    
    private void applyDatabaseSpecific(FluentConfiguration config) {
        // In Flyway 11, database-specific properties are handled via JDBC properties
        Map<String, String> jdbcProps = new HashMap<>();
        
        // Oracle-specific
        if (getBoolean(ORACLE_SQLPLUS)) {
            jdbcProps.put("oracle.sqlplus", "true");
        }
        
        if (getBoolean(ORACLE_SQLPLUS_WARN)) {
            jdbcProps.put("oracle.sqlplus.warn", "true");
        }
        
        String kerberosCacheFile = getProperty(ORACLE_KERBEROS_CACHE_FILE);
        if (kerberosCacheFile != null && !kerberosCacheFile.trim().isEmpty()) {
            jdbcProps.put("oracle.kerberos.cache.file", kerberosCacheFile);
        }
        
        String walletLocation = getProperty(ORACLE_WALLET_LOCATION);
        if (walletLocation != null && !walletLocation.trim().isEmpty()) {
            jdbcProps.put("oracle.wallet.location", walletLocation);
        }
        
        // PostgreSQL-specific
        if (!getBoolean(POSTGRESQL_TRANSACTIONAL_LOCK)) {
            jdbcProps.put("postgresql.transactional.lock", "false");
        }
        
        if (!jdbcProps.isEmpty()) {
            // Merge with existing JDBC properties
            Map<String, String> existingProps = config.getJdbcProperties();
            if (existingProps != null) {
                jdbcProps.putAll(existingProps);
            }
            config.jdbcProperties(jdbcProps);
        }
    }
    
    private void applyPlaceholders(FluentConfiguration config) {
        config.placeholderReplacement(getBoolean(PLACEHOLDER_REPLACEMENT));
        config.placeholderPrefix(getProperty(PLACEHOLDER_PREFIX));
        config.placeholderSuffix(getProperty(PLACEHOLDER_SUFFIX));
        config.placeholderSeparator(getProperty(PLACEHOLDER_SEPARATOR));
        config.scriptPlaceholderPrefix(getProperty(SCRIPT_PLACEHOLDER_PREFIX));
        config.scriptPlaceholderSuffix(getProperty(SCRIPT_PLACEHOLDER_SUFFIX));
        
        // Custom placeholders
        Map<String, String> placeholders = new HashMap<>();
        properties.forEach((key, value) -> {
            if (key.startsWith(PLACEHOLDERS)) {
                String placeholderKey = key.substring(PLACEHOLDERS.length());
                placeholders.put(placeholderKey, value);
                FlywayLogger.debugf("Added placeholder: %s", placeholderKey);
            }
        });
        
        if (!placeholders.isEmpty()) {
            config.placeholders(placeholders);
        }
    }
    
    private void applyCallbacksAndResolvers(FluentConfiguration config) {
        String callbacks = getProperty(CALLBACKS);
        if (callbacks != null && !callbacks.trim().isEmpty()) {
            // Parse and instantiate callbacks
            String[] callbackClasses = callbacks.split(",");
            List<Object> callbackInstances = new ArrayList<>();
            for (String callbackClass : callbackClasses) {
                try {
                    Class<?> clazz = Class.forName(callbackClass.trim());
                    callbackInstances.add(clazz.getDeclaredConstructor().newInstance());
                    FlywayLogger.debugf("Added callback: %s", callbackClass);
                } catch (Exception e) {
                    FlywayLogger.errorf(e, "Failed to instantiate callback: %s", callbackClass);
                }
            }
            if (!callbackInstances.isEmpty()) {
                // Flyway 11 expects callback class names as strings
                String[] callbackNames = new String[callbackClasses.length];
                for (int i = 0; i < callbackClasses.length; i++) {
                    callbackNames[i] = callbackClasses[i].trim();
                }
                config.callbacks(callbackNames);
            }
        }
        
        String resolvers = getProperty(RESOLVERS);
        if (resolvers != null && !resolvers.trim().isEmpty()) {
            // Parse and instantiate resolvers
            String[] resolverClasses = resolvers.split(",");
            List<Object> resolverInstances = new ArrayList<>();
            for (String resolverClass : resolverClasses) {
                try {
                    Class<?> clazz = Class.forName(resolverClass.trim());
                    resolverInstances.add(clazz.getDeclaredConstructor().newInstance());
                    FlywayLogger.debugf("Added resolver: %s", resolverClass);
                } catch (Exception e) {
                    FlywayLogger.errorf(e, "Failed to instantiate resolver: %s", resolverClass);
                }
            }
            if (!resolverInstances.isEmpty()) {
                // Flyway 11 expects resolver class names as strings
                String[] resolverNames = new String[resolverClasses.length];
                for (int i = 0; i < resolverClasses.length; i++) {
                    resolverNames[i] = resolverClasses[i].trim();
                }
                config.resolvers(resolverNames);
            }
        }
    }
    
    private void applyJdbcProperties(FluentConfiguration config) {
        Map<String, String> jdbcProperties = new HashMap<>();
        properties.forEach((key, value) -> {
            if (key.startsWith(JDBC_PROPERTIES)) {
                String jdbcKey = key.substring(JDBC_PROPERTIES.length());
                jdbcProperties.put(jdbcKey, value);
                FlywayLogger.debugf("Added JDBC property: %s", jdbcKey);
            }
        });
        
        if (!jdbcProperties.isEmpty()) {
            config.jdbcProperties(jdbcProperties);
        }
    }
    
    // Helper methods
    private String getProperty(String key) {
        return properties.get(key);
    }
    
    private boolean getBoolean(String key) {
        String value = getProperty(key);
        return Boolean.parseBoolean(value);
    }
    
    private int getInt(String key, int defaultValue) {
        String value = getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                FlywayLogger.warnf("Invalid integer value for %s: %s, using default: %d", 
                    key, value, defaultValue);
            }
        }
        return defaultValue;
    }
    
    public boolean isEnabled() {
        return getBoolean(ENABLED);
    }

}
