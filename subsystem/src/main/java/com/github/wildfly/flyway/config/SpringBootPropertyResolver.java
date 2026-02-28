package com.github.wildfly.flyway.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.dmr.ModelNode;

/**
 * Resolves Spring Boot style Flyway properties with JBoss expression support.
 * <p>
 * Supports:
 * - spring.flyway.* properties
 * - flyway.* properties (normalized to spring.flyway.*)
 * - ${property:default} placeholder syntax
 * - {vendor} placeholder in locations (e.g., db/vendors/{vendor})
 * - Environment variables (SPRING_FLYWAY_* and FLYWAY_*)
 */
public class SpringBootPropertyResolver {

    private static final Logger LOGGER = Logger.getLogger(SpringBootPropertyResolver.class.getName());

    private static final String SPRING_PROPERTY_PREFIX = "spring.flyway.";
    private static final String FLYWAY_PROPERTY_PREFIX = "flyway.";
    private static final Pattern VENDOR_PATTERN = Pattern.compile("\\{vendor}");

    // Security patterns for input validation
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("('.+--)|(--)|(\\*/)|(/\\*)|(char\\()|('\\s*or\\s*'1'\\s*=\\s*'1)|(\\s*or\\s*1\\s*=\\s*1)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("\\.\\.[\\\\/]");
    private static final Pattern JNDI_PATTERN = Pattern.compile("\\$\\{jndi:.*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile("[`$()]|\\$\\(.*\\)");

    private final ExpressionResolver expressionResolver;
    private final String vendor;

    public SpringBootPropertyResolver(ExpressionResolver expressionResolver, String vendor) {
        if (expressionResolver == null) {
            throw new IllegalArgumentException("ExpressionResolver cannot be null");
        }
        this.expressionResolver = expressionResolver;
        this.vendor = sanitizeVendor(vendor);
        LOGGER.log(Level.FINE, "Initialized SpringBootPropertyResolver with vendor: {0}", this.vendor);
    }

    /**
     * Resolve all Flyway properties from various sources.
     */
    public Map<String, String> resolveProperties() {
        LOGGER.log(Level.FINE, "Starting property resolution");
        Map<String, String> properties = new ConcurrentHashMap<>();

        try {
            // 1. System properties
            Properties systemProps = System.getProperties();
            systemProps.forEach((key, value) -> {
                String keyStr = key.toString();
                // Support both spring.flyway.* and flyway.* properties
                if ((keyStr.startsWith(SPRING_PROPERTY_PREFIX) || keyStr.startsWith(FLYWAY_PROPERTY_PREFIX)) 
                        && isValidPropertyKey(keyStr)) {
                    String sanitizedValue = sanitizePropertyValue(value.toString());
                    String resolvedValue = resolvePlaceholders(sanitizedValue);
                    if (resolvedValue != null) {
                        // Normalize to spring.flyway.* format
                        String normalizedKey = normalizePropertyKey(keyStr);
                        properties.put(normalizedKey, resolvedValue);
                        LOGGER.log(Level.FINEST, "Resolved system property: {0}={1}", new Object[]{normalizedKey, maskSensitiveValue(normalizedKey, resolvedValue)});
                    }
                }
            });

            // 2. Environment variables (SPRING_FLYWAY_URL -> spring.flyway.url, FLYWAY_URL -> spring.flyway.url)
            System.getenv().forEach((key, value) -> {
                if ((key.startsWith("SPRING_FLYWAY_") || key.startsWith("FLYWAY_")) && isValidEnvironmentKey(key)) {
                    String propertyKey = key.toLowerCase().replace('_', '.');
                    String normalizedKey = normalizePropertyKey(propertyKey);
                    if (!properties.containsKey(normalizedKey)) { // System properties take precedence
                        String sanitizedValue = sanitizePropertyValue(value);
                        String resolvedValue = resolvePlaceholders(sanitizedValue);
                        if (resolvedValue != null) {
                            properties.put(normalizedKey, resolvedValue);
                            LOGGER.log(Level.FINEST, "Resolved environment variable: {0}={1}", new Object[]{normalizedKey, maskSensitiveValue(normalizedKey, resolvedValue)});
                        }
                    }
                }
            });

            // Apply defaults
            applyDefaults(properties);

            LOGGER.log(Level.FINE, "Property resolution completed. Total properties: {0}", properties.size());
            return properties;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during property resolution", e);
            // Return safe defaults on error
            Map<String, String> safeDefaults = new HashMap<>();
            applyDefaults(safeDefaults);
            return safeDefaults;
        }
    }

    /**
     * Resolve JBoss/WildFly expression placeholders.
     */
    private String resolvePlaceholders(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        try {
            // First resolve JBoss expressions ${property:default}
            if (value.contains("${") && !containsMaliciousExpression(value)) {
                try {
                    ModelNode node = ModelNode.fromString("\"" + value + "\"");
                    ModelNode resolved = expressionResolver.resolveExpressions(node);
                    value = resolved.asString();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to resolve expression: " + value + ", using original value", e);
                    // If expression resolution fails, use original value
                }
            }

            // Then resolve vendor placeholders
            if (vendor != null && value.contains("{vendor}")) {
                value = VENDOR_PATTERN.matcher(value).replaceAll(vendor);
            }

            return value;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in placeholder resolution", e);
            return value; // Return original on any unexpected error
        }
    }

    /**
     * Apply Spring Boot default values.
     */
    private void applyDefaults(Map<String, String> properties) {
        // Spring Boot defaults
        properties.putIfAbsent("spring.flyway.enabled", "true");
        properties.putIfAbsent("spring.flyway.locations", "classpath:db/migration");
        properties.putIfAbsent("spring.flyway.baseline-on-migrate", "false");
        properties.putIfAbsent("spring.flyway.check-location", "true");
        properties.putIfAbsent("spring.flyway.clean-disabled", "true");
        properties.putIfAbsent("spring.flyway.validate-on-migrate", "true");
        properties.putIfAbsent("spring.flyway.sql-migration-prefix", "V");
        properties.putIfAbsent("spring.flyway.sql-migration-suffixes", ".sql");
        properties.putIfAbsent("spring.flyway.sql-migration-separator", "__");
        properties.putIfAbsent("spring.flyway.repeatable-sql-migration-prefix", "R");
    }

    /**
     * Normalize property key to spring.flyway.* format.
     */
    private String normalizePropertyKey(String key) {
        if (key == null) {
            return null;
        }
        
        // If it already starts with spring.flyway., return as is
        if (key.startsWith(SPRING_PROPERTY_PREFIX)) {
            return key;
        }
        
        // If it starts with flyway., convert to spring.flyway.
        if (key.startsWith(FLYWAY_PROPERTY_PREFIX)) {
            return SPRING_PROPERTY_PREFIX + key.substring(FLYWAY_PROPERTY_PREFIX.length());
        }
        
        // Otherwise return as is (shouldn't happen with our checks)
        return key;
    }

    /**
     * Get database vendor from datasource URL.
     */
    public static String detectVendor(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return null;
        }

        String url = jdbcUrl.toLowerCase();

        if (url.contains(":h2:")) {
            return "h2";
        } else if (url.contains(":postgresql:")) {
            return "postgresql";
        } else if (url.contains(":mysql:")) {
            return "mysql";
        } else if (url.contains(":mariadb:")) {
            return "mariadb";
        } else if (url.contains(":oracle:")) {
            return "oracle";
        } else if (url.contains(":sqlserver:") || url.contains(":jtds:")) {
            return "sqlserver";
        } else if (url.contains(":db2:")) {
            return "db2";
        }

        LOGGER.log(Level.FINE, "Unknown database vendor for URL: {0}", jdbcUrl);
        return null;
    }

    // ===== Security and Validation Methods =====

    private String sanitizePropertyValue(String value) {
        if (value == null) {
            return null;
        }

        // Remove potentially dangerous patterns
        String sanitized = value;
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = SQL_INJECTION_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = JNDI_PATTERN.matcher(sanitized).replaceAll("");

        // For file paths, normalize and prevent traversal
        if (isFilePathProperty(value)) {
            sanitized = sanitizeFilePath(sanitized);
        }

        // Log if sanitization was needed
        if (!value.equals(sanitized)) {
            LOGGER.log(Level.WARNING, "Property value was sanitized for security. Original length: {0}, Sanitized length: {1}",
                    new Object[]{value.length(), sanitized.length()});
        }

        return sanitized;
    }

    private String sanitizeFilePath(String path) {
        if (path == null) {
            return null;
        }

        // Remove path traversal attempts
        String sanitized = PATH_TRAVERSAL_PATTERN.matcher(path).replaceAll("");

        // Remove command injection attempts
        sanitized = COMMAND_INJECTION_PATTERN.matcher(sanitized).replaceAll("");

        return sanitized;
    }

    private String sanitizeVendor(String vendor) {
        if (vendor == null) {
            return null;
        }

        // Only allow alphanumeric and hyphen for vendor names
        String sanitized = vendor.replaceAll("[^a-zA-Z0-9-]", "");

        if (!vendor.equals(sanitized)) {
            LOGGER.log(Level.WARNING, "Vendor name was sanitized: {0} -> {1}", new Object[]{vendor, sanitized});
        }

        return sanitized;
    }

    private boolean isValidPropertyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        // Property keys should only contain alphanumeric, dots, hyphens, and underscores
        return key.matches("^[a-zA-Z0-9._-]+$");
    }

    private boolean isValidEnvironmentKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        // Environment keys should only contain alphanumeric and underscores
        return key.matches("^[A-Z0-9_]+$");
    }

    private boolean containsMaliciousExpression(String value) {
        // Check for JNDI injection attempts
        if (JNDI_PATTERN.matcher(value).find()) {
            LOGGER.log(Level.WARNING, "Detected potential JNDI injection attempt");
            return true;
        }

        // Check for nested expressions that could cause recursion
        int openCount = 0;
        int closeCount = 0;
        for (char c : value.toCharArray()) {
            if (c == '{') openCount++;
            if (c == '}') closeCount++;
        }

        if (openCount != closeCount || openCount > 3) {
            LOGGER.log(Level.WARNING, "Detected potentially malicious expression nesting");
            return true;
        }

        return false;
    }

    private boolean isFilePathProperty(String value) {
        return value.contains("/") || value.contains("\\") ||
                value.startsWith("classpath:") || value.startsWith("filesystem:");
    }

    private String maskSensitiveValue(String key, String value) {
        if (key == null || value == null) {
            return value;
        }

        // Mask sensitive properties
        if (key.toLowerCase().contains("password") ||
                key.toLowerCase().contains("secret") ||
                key.toLowerCase().contains("token") ||
                key.toLowerCase().contains("key")) {
            return "***MASKED***";
        }

        return value;
    }
}
