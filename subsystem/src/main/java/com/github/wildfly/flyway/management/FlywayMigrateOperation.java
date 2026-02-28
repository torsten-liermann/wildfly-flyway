package com.github.wildfly.flyway.management;

import com.github.wildfly.flyway.logging.FlywayLogger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jboss.as.controller.*;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Operation handler for executing Flyway migrations via CLI.
 * Example: /subsystem=flyway/migration=flyway:migrate
 */
public class FlywayMigrateOperation implements OperationStepHandler {
    
    public static final FlywayMigrateOperation INSTANCE = new FlywayMigrateOperation();
    
    // The data-source capability name from WildFly
    private static final String DATA_SOURCE_CAPABILITY_NAME = "org.wildfly.data-source";
    
    private static final SimpleAttributeDefinition TARGET = new SimpleAttributeDefinitionBuilder("target", ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(false)
            .build();
    
    private static final SimpleAttributeDefinition OUT_OF_ORDER = new SimpleAttributeDefinitionBuilder("out-of-order", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(false)
            .build();
    
    private static final SimpleAttributeDefinition SKIP_EXECUTING_MIGRATIONS = new SimpleAttributeDefinitionBuilder("skip-executing-migrations", ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(false)
            .build();
    
    static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("migrate", 
            FlywayManagementResourceDefinition.RESOLVER)
            .setParameters(TARGET, OUT_OF_ORDER, SKIP_EXECUTING_MIGRATIONS)
            .setRuntimeOnly()
            .setReplyType(ModelType.OBJECT)
            .setReplyValueType(ModelType.OBJECT)
            .build();
    
    private FlywayMigrateOperation() {
    }
    
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        
        context.addStep((context1, operation1) -> {

            // Read datasource name with null check
            ModelNode datasourceNode = context1.readResource(PathAddress.EMPTY_ADDRESS)
                    .getModel()
                    .get(FlywayManagementResourceDefinition.DATASOURCE.getName());
            if (!datasourceNode.isDefined()) {
                throw new OperationFailedException(
                    "No datasource configured for this Flyway migration resource. " +
                    "Set the 'datasource' attribute to the JNDI name of the target datasource.");
            }
            final String datasourceName = datasourceNode.asString();
            if (datasourceName.isBlank()) {
                throw new OperationFailedException(
                    "Datasource name is empty. Provide a valid JNDI name (e.g., 'java:jboss/datasources/MyDS').");
            }

            // Get optional parameters
            final String target = TARGET.resolveValue(context1, operation1).asStringOrNull();
            final boolean outOfOrder = OUT_OF_ORDER.resolveValue(context1, operation1).asBoolean();
            final boolean skipExecutingMigrations = SKIP_EXECUTING_MIGRATIONS.resolveValue(context1, operation1).asBoolean();

            try {
                // Resolve datasource: if it looks like a JNDI name, strip the prefix for capability lookup
                String capabilityName = datasourceName;
                if (capabilityName.startsWith("java:jboss/datasources/")) {
                    capabilityName = capabilityName.substring("java:jboss/datasources/".length());
                } else if (capabilityName.startsWith("java:/")) {
                    capabilityName = capabilityName.substring("java:/".length());
                }

                // Get the datasource using capability service name
                ServiceName datasourceServiceName = context1.getCapabilityServiceName(
                    DATA_SOURCE_CAPABILITY_NAME, capabilityName, DataSource.class);
                ServiceController<?> datasourceService = context1.getServiceRegistry(false)
                    .getRequiredService(datasourceServiceName);

                // For runtime operations, getValue() is still the standard approach
                // as the service is already started and we need immediate access
                DataSource dataSource = (DataSource) datasourceService.getValue();

                // Configure Flyway
                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(false)
                        .cleanDisabled(true)
                        .outOfOrder(outOfOrder)
                        .skipExecutingMigrations(skipExecutingMigrations)
                        .target(target)
                        .load();

                // Execute migration
                FlywayLogger.ROOT_LOGGER.infof("Executing Flyway migration for datasource: %s", datasourceName);
                MigrateResult result = flyway.migrate();

                // Build result
                ModelNode resultNode = new ModelNode();
                resultNode.get("success").set(result.success);
                resultNode.get("migrationsExecuted").set(result.migrationsExecuted);
                resultNode.get("database").set(result.database);
                resultNode.get("targetSchemaVersion").set(result.targetSchemaVersion != null ?
                        result.targetSchemaVersion : "latest");

                if (result.migrations != null) {
                    ModelNode migrationsNode = resultNode.get("migrations").setEmptyList();
                    for (var migration : result.migrations) {
                        ModelNode migrationNode = new ModelNode();
                        migrationNode.get("version").set(migration.version != null ?
                                migration.version : "repeatable");
                        migrationNode.get("description").set(migration.description);
                        migrationNode.get("type").set(migration.type);
                        migrationNode.get("executionTime").set(migration.executionTime);
                        migrationsNode.add(migrationNode);
                    }
                }

                if (result.warnings != null && !result.warnings.isEmpty()) {
                    ModelNode warningsNode = resultNode.get("warnings").setEmptyList();
                    for (String warning : result.warnings) {
                        warningsNode.add(warning);
                    }
                }

                context1.getResult().set(resultNode);

                FlywayLogger.ROOT_LOGGER.infof("Migration completed successfully. Migrations executed: %d",
                        result.migrationsExecuted);

            } catch (Exception e) {
                throw new OperationFailedException("Failed to execute migration: " + e.getMessage(), e);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
