package com.github.wildfly.flyway.config;

import com.github.wildfly.flyway.logging.FlywayLogger;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jboss.as.ee.metadata.property.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.metadata.property.PropertyReplacer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Central builder for Flyway configuration that handles the three-tier configuration hierarchy:
 * 1. Deployment properties (highest priority)
 * 2. Subsystem configuration
 * 3. Auto-discovery (opt-in only)
 * 
 * This class consolidates all configuration logic that was previously spread across
 * FlywayDeploymentProcessor and FlywayMigrationService.
 */
public class FlywayConfigurationBuilder {

    private final DeploymentPhaseContext phaseContext;
    private final DeploymentUnit deploymentUnit;
    private final Properties deploymentProperties;
    
    // Configuration state
    private String datasourceJndiName;
    private DataSource dataSource;
    private final Map<String, String> flywayProperties = new HashMap<>();
    private boolean isFromSubsystem = false;
    
    public FlywayConfigurationBuilder(DeploymentPhaseContext phaseContext, 
                                    DeploymentUnit deploymentUnit,
                                    Properties deploymentProperties) {
        this.phaseContext = phaseContext;
        this.deploymentUnit = deploymentUnit;
        this.deploymentProperties = deploymentProperties != null ? deploymentProperties : new Properties();
        
        FlywayLogger.debugf("FlywayConfigurationBuilder initialized");
    }
    
    /**
     * Build the complete Flyway configuration following the three-tier hierarchy.
     * 
     * IMPORTANT: The hierarchy works as follows:
     * 1. ALWAYS start with subsystem configuration as the base
     * 2. Overlay deployment properties on top
     * 3. Determine datasource from: deployment properties > subsystem default-datasource > auto-discovery
     * 
     * @return ConfigurationResult containing datasource JNDI name and all configuration properties
     * @throws Exception if configuration cannot be determined
     */
    public ConfigurationResult build() throws Exception {
        FlywayLogger.infof("Building Flyway configuration for deployment: %s", deploymentUnit.getName());
        
        // ALWAYS start with subsystem configuration as the base
        Properties subsystemProps = readSubsystemConfiguration();
        
        // Add all subsystem properties as base configuration
        for (String key : subsystemProps.stringPropertyNames()) {
            String value = subsystemProps.getProperty(key);
            String flywayKey = convertToFlywayKey(key);
            if (flywayKey != null) {
                flywayProperties.put(flywayKey, value);
                FlywayLogger.debugf("Base config from subsystem: %s = %s", flywayKey, 
                                  key.contains("datasource") ? maskDataSource(value) : value);
            }
        }
        
        // Overlay deployment properties (they override subsystem defaults)
        collectDeploymentProperties();
        
        // Now determine datasource using the hierarchy
        // 1. Check deployment properties DIRECTLY first (not the merged map,
        //    because the merged map also contains subsystem defaults)
        String ds = deploymentProperties.getProperty("flyway.datasource");
        if (ds == null || ds.trim().isEmpty()) {
            ds = deploymentProperties.getProperty("spring.flyway.datasource");
        }
        if (ds != null && !ds.trim().isEmpty()) {
            datasourceJndiName = resolveExpression(ds);
            isFromSubsystem = false;
            FlywayLogger.infof("Using datasource from deployment properties: %s", maskDataSource(datasourceJndiName));
            return createResult();
        }

        // 2. Use subsystem default-datasource if available
        ds = subsystemProps.getProperty("default-datasource");
        if (ds != null && !ds.trim().isEmpty()) {
            datasourceJndiName = resolveExpression(ds);
            isFromSubsystem = true;
            FlywayLogger.infof("Using default datasource from subsystem configuration: %s", maskDataSource(datasourceJndiName));
            return createResult();
        }
        
        // 3. Try auto-discovery (only if explicitly enabled)
        if (isAutoDiscoveryEnabled() && tryAutoDiscovery()) {
            FlywayLogger.infof("Using auto-discovered datasource: %s", maskDataSource(datasourceJndiName));
            return createResult();
        }
        
        throw new Exception("No datasource configured for Flyway. Please specify a datasource in " +
                          "META-INF/flyway.properties, subsystem configuration, or enable auto-discovery.");
    }
    
    /**
     * Apply configuration to a Flyway FluentConfiguration.
     * This is a static utility method that can be used independently.
     */
    public static void applyToFlyway(FluentConfiguration flywayConfig, 
                                   DataSource dataSource, 
                                   ClassLoader classLoader,
                                   Map<String, String> properties) {
        // Set datasource
        flywayConfig.dataSource(dataSource);
        
        // Detect database vendor for {vendor} placeholder replacement
        String vendor = detectVendorStatic(dataSource);
        
        // Replace {vendor} placeholders in locations if needed
        Map<String, String> processedProperties = new HashMap<>(properties);
        if (processedProperties.containsKey("spring.flyway.locations") && vendor != null) {
            String locations = processedProperties.get("spring.flyway.locations");
            if (locations.contains("{vendor}")) {
                locations = locations.replace("{vendor}", vendor);
                processedProperties.put("spring.flyway.locations", locations);
                FlywayLogger.debugf("Replaced vendor placeholder in locations: %s", locations);
            }
        }
        
        // Apply all Flyway properties
        FlywayConfiguration config = new FlywayConfiguration(processedProperties);
        config.applyTo(flywayConfig);
    }
    
    private static String detectVendorStatic(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            return SpringBootPropertyResolver.detectVendor(jdbcUrl);
        } catch (SQLException e) {
            FlywayLogger.warnf("Failed to detect database vendor: %s", e.getMessage());
            return null;
        }
    }
    
    
    private boolean tryAutoDiscovery() {
        // Auto-discovery logic would go here
        // For now, returning false as it's not implemented in the original code
        FlywayLogger.warn("Auto-discovery not yet implemented");
        return false;
    }
    
    private boolean isAutoDiscoveryEnabled() {
        String enabled = getPropertyValue("auto-discovery.enabled", "false");
        return "true".equalsIgnoreCase(enabled);
    }
    
    private void collectDeploymentProperties() {
        // Process all deployment properties
        for (String key : deploymentProperties.stringPropertyNames()) {
            String value = deploymentProperties.getProperty(key);
            
            // Handle both namespaces
            if (key.startsWith("flyway.") || key.startsWith("spring.flyway.")) {
                // Normalize to spring.flyway.*
                String normalizedKey = normalizeKey(key);
                
                FlywayLogger.debugf("Processing deployment property: %s = %s", key, value);
                
                // Resolve expressions using WildFly's expression resolver
                String resolvedValue = resolveExpression(value);
                
                FlywayLogger.debugf("After expression resolution: %s = %s", key, resolvedValue);
                
                // Store (will override any existing value from subsystem)
                flywayProperties.put(normalizedKey, resolvedValue);
                
                FlywayLogger.debugf("Deployment property: %s = %s", normalizedKey, 
                                  key.toLowerCase().contains("password") ? "***" : resolvedValue);
            }
        }
    }
    
    
    private Properties readSubsystemConfiguration() {
        FlywayLogger.debugf("Reading subsystem configuration for deployment: %s", deploymentUnit.getName());

        Properties props = SubsystemConfigurationHolder.getConfiguration();

        if (props.isEmpty()) {
            FlywayLogger.debugf("No subsystem configuration available (subsystem not configured or disabled)");
        } else {
            FlywayLogger.debugf("Loaded %d subsystem properties from boot-phase configuration", props.size());
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                FlywayLogger.debugf("Subsystem property: %s = %s", key,
                                  key.contains("datasource") ? maskDataSource(value) : value);
            }
        }

        return props;
    }
    
    private String getPropertyValue(String key, String defaultValue) {
        // Check the merged flywayProperties which includes both subsystem and deployment props
        String normalizedKey = "spring.flyway." + key;
        String value = flywayProperties.get(normalizedKey);
        FlywayLogger.debugf("getPropertyValue(%s): checking merged properties, found: %s", key, value);
        if (value != null) {
            return value;
        }
        
        // Also check direct deployment properties
        // First try flyway.* namespace (higher priority)
        value = deploymentProperties.getProperty("flyway." + key);
        FlywayLogger.debugf("getPropertyValue(%s): checking flyway.%s, found: %s", key, key, value);
        if (value != null) {
            return value;
        }
        
        // Then try spring.flyway.* namespace
        value = deploymentProperties.getProperty("spring.flyway." + key);
        FlywayLogger.debugf("getPropertyValue(%s): checking spring.flyway.%s, found: %s", key, key, value);
        if (value != null) {
            return value;
        }
        
        return defaultValue;
    }
    
    private String normalizeKey(String key) {
        if (key.startsWith("spring.flyway.")) {
            return key;
        } else if (key.startsWith("flyway.")) {
            return "spring.flyway." + key.substring("flyway.".length());
        }
        return null;
    }
    
    private String convertToFlywayKey(String subsystemKey) {
        // Map subsystem attributes to flyway properties
        switch (subsystemKey) {
            case "enabled":
                return "spring.flyway.enabled";
            case "default-datasource":
                return "spring.flyway.datasource";
            case "baseline-on-migrate":
                return "spring.flyway.baseline-on-migrate";
            case "clean-disabled":
                return "spring.flyway.clean-disabled";
            case "validate-on-migrate":
                return "spring.flyway.validate-on-migrate";
            case "locations":
                return "spring.flyway.locations";
            case "table":
                return "spring.flyway.table";
            default:
                return null;
        }
    }
    
    private String resolveExpression(String value) {
        if (value == null) {
            return null;
        }
        
        // Check if value contains expressions like ${...}
        if (!value.contains("${")) {
            return value;
        }
        
        // Get PropertyReplacer from deployment unit
        PropertyReplacer propertyReplacer = deploymentUnit.getAttachment(Attachments.FINAL_PROPERTY_REPLACER);
        if (propertyReplacer != null) {
            try {
                return propertyReplacer.replaceProperties(value);
            } catch (Exception e) {
                FlywayLogger.debugf("Failed to resolve expression '%s': %s", value, e.getMessage());
            }
        }
        
        // Fallback to simple system property resolution
        return resolveSimpleExpression(value);
    }
    
    /**
     * Simple fallback expression resolution for system properties and environment variables.
     */
    private String resolveSimpleExpression(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        String result = value;
        int startIdx = 0;
        while (startIdx < result.length()) {
            int start = result.indexOf("${", startIdx);
            if (start == -1) {
                break;
            }
            int end = result.indexOf("}", start);
            if (end == -1) {
                break;
            }
            
            String expression = result.substring(start + 2, end);
            String replacement = null;
            
            // Handle ${env.NAME:default} pattern
            if (expression.startsWith("env.")) {
                String[] parts = expression.substring(4).split(":", 2);
                String envName = parts[0];
                String defaultValue = parts.length > 1 ? parts[1] : "";
                replacement = System.getenv(envName);
                if (replacement == null) {
                    replacement = defaultValue;
                }
            }
            // Handle ${sys.NAME:default} or ${NAME:default} pattern for system properties
            else {
                String propExpression = expression.startsWith("sys.") ? expression.substring(4) : expression;
                String[] parts = propExpression.split(":", 2);
                String propName = parts[0];
                String defaultValue = parts.length > 1 ? parts[1] : "";
                replacement = System.getProperty(propName);
                if (replacement == null) {
                    replacement = defaultValue;
                }
            }
            
            if (replacement != null) {
                result = result.substring(0, start) + replacement + result.substring(end + 1);
                startIdx = start + replacement.length();
            } else {
                startIdx = end + 1;
            }
        }
        
        return result;
    }
    
    private String detectVendor(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            return SpringBootPropertyResolver.detectVendor(jdbcUrl);
        } catch (SQLException e) {
            FlywayLogger.warnf("Failed to detect database vendor: %s", e.getMessage());
            return null;
        }
    }
    
    private ConfigurationResult createResult() {
        // Always set enabled=true if we have a datasource
        if (!flywayProperties.containsKey("spring.flyway.enabled")) {
            flywayProperties.put("spring.flyway.enabled", "true");
        }
        
        return new ConfigurationResult(datasourceJndiName, flywayProperties, isFromSubsystem);
    }
    
    private String maskDataSource(String value) {
        if (value == null || !value.contains("datasources")) {
            return value;
        }
        // Mask the datasource name but keep the structure visible
        return value.replaceAll("/([^/]+)$", "/***");
    }
    
    /**
     * Result of configuration building.
     */
    public static class ConfigurationResult {
        private final String datasourceJndiName;
        private final Map<String, String> flywayProperties;
        private final boolean fromSubsystem;
        
        public ConfigurationResult(String datasourceJndiName, 
                                 Map<String, String> flywayProperties,
                                 boolean fromSubsystem) {
            this.datasourceJndiName = datasourceJndiName;
            this.flywayProperties = new HashMap<>(flywayProperties);
            this.fromSubsystem = fromSubsystem;
        }
        
        public String getDatasourceJndiName() {
            return datasourceJndiName;
        }
        
        public Map<String, String> getFlywayProperties() {
            return new HashMap<>(flywayProperties);
        }
        
        public boolean isFromSubsystem() {
            return fromSubsystem;
        }
    }
}