package com.github.wildfly.flyway.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

/**
 * Flyway subsystem logger with enhanced capabilities.
 * Provides comprehensive logging with performance tracking and security masking.
 */
public class FlywayLogger {

    public static final Logger ROOT_LOGGER = Logger.getLogger("com.github.wildfly.flyway");
    
    // Category-specific loggers
    private static final Logger MIGRATION_LOGGER = Logger.getLogger("com.github.wildfly.flyway.migration");
    private static final Logger CONFIG_LOGGER = Logger.getLogger("com.github.wildfly.flyway.config");
    private static final Logger SECURITY_LOGGER = Logger.getLogger("com.github.wildfly.flyway.security");
    private static final Logger PERFORMANCE_LOGGER = Logger.getLogger("com.github.wildfly.flyway.performance");
    
    // Performance tracking
    private static final ConcurrentHashMap<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> operationTimes = new ConcurrentHashMap<>();
    
    // Log level checks for performance
    public static boolean isDebugEnabled() {
        return ROOT_LOGGER.isDebugEnabled();
    }
    
    public static boolean isTraceEnabled() {
        return ROOT_LOGGER.isTraceEnabled();
    }
    
    // Basic logging methods
    public static void info(String message) {
        ROOT_LOGGER.info(message);
    }

    public static void warn(String message) {
        ROOT_LOGGER.warn(message);
    }

    public static void error(String message) {
        ROOT_LOGGER.error(message);
    }
    
    public static void error(String message, Throwable throwable) {
        ROOT_LOGGER.error(message, throwable);
    }

    public static void debug(String message) {
        if (ROOT_LOGGER.isDebugEnabled()) {
            ROOT_LOGGER.debug(message);
        }
    }
    
    public static void trace(String message) {
        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.trace(message);
        }
    }

    // Formatted logging methods
    public static void infof(String format, Object... args) {
        ROOT_LOGGER.infof(format, args);
    }
    
    public static void warnf(String format, Object... args) {
        ROOT_LOGGER.warnf(format, args);
    }
    
    public static void errorf(String format, Object... args) {
        ROOT_LOGGER.errorf(format, args);
    }
    
    public static void errorf(Throwable throwable, String format, Object... args) {
        ROOT_LOGGER.errorf(throwable, format, args);
    }
    
    public static void debugf(String format, Object... args) {
        if (ROOT_LOGGER.isDebugEnabled()) {
            ROOT_LOGGER.debugf(format, args);
        }
    }
    
    public static void tracef(String format, Object... args) {
        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.tracef(format, args);
        }
    }
    
    // Category-specific logging
    public static void logMigration(Level level, String message, Object... args) {
        MIGRATION_LOGGER.logf(level, message, args);
    }
    
    public static void logConfig(Level level, String message, Object... args) {
        CONFIG_LOGGER.logf(level, message, args);
    }
    
    public static void logSecurity(Level level, String message, Object... args) {
        SECURITY_LOGGER.logf(level, message, args);
        
        // Always log security events at WARN or higher
        if (level.ordinal() <= Level.WARN.ordinal()) {
            operationCounts.computeIfAbsent("security.events", k -> new AtomicLong()).incrementAndGet();
        }
    }
    
    public static void logPerformance(String operation, long durationMs) {
        if (PERFORMANCE_LOGGER.isDebugEnabled()) {
            PERFORMANCE_LOGGER.debugf("Operation [%s] completed in %d ms", operation, durationMs);
        }
        
        // Track performance metrics
        operationCounts.computeIfAbsent(operation, k -> new AtomicLong()).incrementAndGet();
        operationTimes.computeIfAbsent(operation, k -> new AtomicLong()).addAndGet(durationMs);
    }
    
    // Performance tracking methods
    public static long startOperation(String operation) {
        debugf("Starting operation: %s", operation);
        return System.currentTimeMillis();
    }
    
    public static void endOperation(String operation, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        logPerformance(operation, duration);
    }
    
    // Security masking for sensitive data
    public static String maskSensitive(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
    
    public static String maskPassword(String value) {
        return "***MASKED***";
    }
    
    public static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        
        // Mask password in JDBC URL
        String masked = jdbcUrl.replaceAll("password=[^;&]*", "password=***")
                               .replaceAll(":[^:@]+@", ":***@"); // user:password@host pattern
        
        // Mask any query parameters that might contain sensitive data
        masked = masked.replaceAll("(secret|token|key|pwd)=[^&]*", "$1=***");
        
        return masked;
    }
    
    // Deployment context logging
    public static void logDeploymentStart(String deploymentName) {
        infof("===== Starting Flyway operations for deployment: %s =====", deploymentName);
    }
    
    public static void logDeploymentEnd(String deploymentName, boolean success) {
        if (success) {
            infof("===== Completed Flyway operations for deployment: %s =====", deploymentName);
        } else {
            errorf("===== Failed Flyway operations for deployment: %s =====", deploymentName);
        }
    }
    
    // Statistics and metrics
    public static void logStatistics() {
        if (!PERFORMANCE_LOGGER.isInfoEnabled()) {
            return;
        }
        
        PERFORMANCE_LOGGER.info("===== Flyway Performance Statistics =====");
        operationCounts.forEach((operation, count) -> {
            AtomicLong totalTime = operationTimes.get(operation);
            if (totalTime != null && count.get() > 0) {
                long avgTime = totalTime.get() / count.get();
                PERFORMANCE_LOGGER.infof("  %s: %d operations, avg time: %d ms", 
                    operation, count.get(), avgTime);
            } else {
                PERFORMANCE_LOGGER.infof("  %s: %d operations", operation, count.get());
            }
        });
    }
    
    public static void clearStatistics() {
        operationCounts.clear();
        operationTimes.clear();
    }
}
