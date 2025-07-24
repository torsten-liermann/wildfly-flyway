package com.github.wildfly.flyway.management;

import com.github.wildfly.flyway.extension.FlywayExtension;
import com.github.wildfly.flyway.logging.FlywayLogger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.jboss.as.controller.*;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Management resource definition for Flyway operations.
 * Provides CLI commands for migration management.
 */
public class FlywayManagementResourceDefinition extends SimpleResourceDefinition {
    
    static final PathElement PATH_ELEMENT = PathElement.pathElement("migration", "flyway");
    
    static final StandardResourceDescriptionResolver RESOLVER = 
        new StandardResourceDescriptionResolver(
            FlywayExtension.SUBSYSTEM_NAME, 
            "com.github.wildfly.flyway.extension.LocalDescriptions",
            FlywayManagementResourceDefinition.class.getClassLoader());
    
    // Attributes
    static final SimpleAttributeDefinition DATASOURCE = new SimpleAttributeDefinitionBuilder("datasource", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final SimpleAttributeDefinition LOCATIONS = new SimpleAttributeDefinitionBuilder("locations", ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("classpath:db/migration"))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final SimpleAttributeDefinition BASELINE_ON_MIGRATE = new SimpleAttributeDefinitionBuilder("baseline-on-migrate", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final SimpleAttributeDefinition CLEAN_DISABLED = new SimpleAttributeDefinitionBuilder("clean-disabled", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final Collection<AttributeDefinition> ATTRIBUTES = Collections.unmodifiableCollection(
        Arrays.asList(DATASOURCE, ENABLED, LOCATIONS, BASELINE_ON_MIGRATE, CLEAN_DISABLED));
    
    public FlywayManagementResourceDefinition() {
        super(new Parameters(PATH_ELEMENT, RESOLVER)
                .setAddHandler(FlywayMigrationAddHandler.INSTANCE)
                .setRemoveHandler(FlywayMigrationRemoveHandler.INSTANCE));
    }
    
    public Collection<AttributeDefinition> getAttributes() {
        return ATTRIBUTES;
    }
    
    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        
        // Register management operations
        resourceRegistration.registerOperationHandler(FlywayMigrateOperation.DEFINITION, FlywayMigrateOperation.INSTANCE);
        // TODO: Add other operations (validate, info, repair, baseline, clean) as needed
    }
    
    // Add handler
    static class FlywayMigrationAddHandler extends AbstractAddStepHandler {
        static final FlywayMigrationAddHandler INSTANCE = new FlywayMigrationAddHandler();
        
        private FlywayMigrationAddHandler() {
            super(ATTRIBUTES);
        }
        
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) 
                throws OperationFailedException {
            FlywayLogger.ROOT_LOGGER.info("Adding Flyway migration configuration");
            // Service installation would happen here
        }
    }
    
    // Remove handler
    static class FlywayMigrationRemoveHandler extends AbstractRemoveStepHandler {
        static final FlywayMigrationRemoveHandler INSTANCE = new FlywayMigrationRemoveHandler();
        
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) 
                throws OperationFailedException {
            FlywayLogger.ROOT_LOGGER.info("Removing Flyway migration configuration");
            // Service removal would happen here
        }
    }
}
