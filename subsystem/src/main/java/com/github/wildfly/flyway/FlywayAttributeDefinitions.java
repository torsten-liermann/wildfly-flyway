package com.github.wildfly.flyway;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Attribute definitions for Flyway configuration properties.
 * These definitions support expression resolution including environment variables.
 */
public class FlywayAttributeDefinitions {

    // Datasource configuration
    public static final SimpleAttributeDefinition DATASOURCE = new SimpleAttributeDefinitionBuilder("datasource", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition DEFAULT_DATASOURCE = new SimpleAttributeDefinitionBuilder("default-datasource", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    // Migration configuration
    public static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition BASELINE_ON_MIGRATE = new SimpleAttributeDefinitionBuilder("baseline-on-migrate", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition CLEAN_DISABLED = new SimpleAttributeDefinitionBuilder("clean-disabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition VALIDATE_ON_MIGRATE = new SimpleAttributeDefinitionBuilder("validate-on-migrate", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition LOCATIONS = new SimpleAttributeDefinitionBuilder("locations", ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("classpath:db/migration"))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition TABLE = new SimpleAttributeDefinitionBuilder("table", ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("flyway_schema_history"))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition AUTO_DISCOVERY_ENABLED = new SimpleAttributeDefinitionBuilder("auto-discovery.enabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    // Baseline configuration
    public static final SimpleAttributeDefinition BASELINE_VERSION = new SimpleAttributeDefinitionBuilder("baseline-version", ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("1"))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition BASELINE_DESCRIPTION = new SimpleAttributeDefinitionBuilder("baseline-description", ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("<< Flyway Baseline >>"))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private FlywayAttributeDefinitions() {
        // Utility class
    }
}