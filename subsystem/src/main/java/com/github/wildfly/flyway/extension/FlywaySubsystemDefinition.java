package com.github.wildfly.flyway.extension;

import com.github.wildfly.flyway.deployment.FlywayDeploymentProcessor;
import com.github.wildfly.flyway.management.FlywayManagementResourceDefinition;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Flyway subsystem definition - simple subsystem with minimal configuration.
 */
public class FlywaySubsystemDefinition extends SimpleResourceDefinition {
    
    static final AttributeDefinition ENABLED = SimpleAttributeDefinitionBuilder
            .create("enabled", ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.TRUE)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    static final AttributeDefinition DEFAULT_DATASOURCE = SimpleAttributeDefinitionBuilder
            .create("default-datasource", ModelType.STRING)
            .setDefaultValue(new ModelNode("ExampleDS"))
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    
    private static final Collection<AttributeDefinition> ATTRIBUTES = Collections.unmodifiableList(
            Arrays.asList(ENABLED, DEFAULT_DATASOURCE));
    
    FlywaySubsystemDefinition() {
        super(FlywayExtension.SUBSYSTEM_PATH,
                FlywayExtension.getResourceDescriptionResolver(),
                FlywaySubsystemAdd.INSTANCE,
                FlywaySubsystemRemove.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        // Register the attributes for read/write
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, new ReloadRequiredWriteAttributeHandler(attr));
        }
    }
    
    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(
                GenericSubsystemDescribeHandler.DEFINITION,
                GenericSubsystemDescribeHandler.INSTANCE);
    }
    
    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        // Register the management resource for Flyway operations
        resourceRegistration.registerSubModel(new FlywayManagementResourceDefinition());
    }
    
    /**
     * Handler for adding the subsystem.
     */
    static class FlywaySubsystemAdd extends AbstractBoottimeAddStepHandler {
        
        static final FlywaySubsystemAdd INSTANCE = new FlywaySubsystemAdd();
        
        private FlywaySubsystemAdd() {
            super(ATTRIBUTES);
        }
        
        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            
            // Check if subsystem is enabled
            boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();
            if (!enabled) {
                com.github.wildfly.flyway.logging.FlywayLogger.info("Flyway subsystem is disabled");
                return;
            }
            
            // Register deployment processor
            context.addStep(new AbstractDeploymentChainStep() {
                @Override
                protected void execute(DeploymentProcessorTarget processorTarget) {
                    processorTarget.addDeploymentProcessor(
                            FlywayExtension.SUBSYSTEM_NAME,
                            Phase.POST_MODULE,
                            0x3800 + 0x100,
                            new FlywayDeploymentProcessor());
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }
    
    /**
     * Handler for removing the subsystem.
     */
    static class FlywaySubsystemRemove extends AbstractRemoveStepHandler {
        
        static final FlywaySubsystemRemove INSTANCE = new FlywaySubsystemRemove();
        
        private FlywaySubsystemRemove() {
        }
        
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
            // Remove any runtime services if needed
        }
    }
}
