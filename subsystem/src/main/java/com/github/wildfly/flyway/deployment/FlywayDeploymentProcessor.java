package com.github.wildfly.flyway.deployment;

import com.github.wildfly.flyway.config.FlywayConfigurationBuilder;
import com.github.wildfly.flyway.config.FlywayConfigurationBuilder.ConfigurationResult;
import com.github.wildfly.flyway.logging.FlywayLogger;
import com.github.wildfly.flyway.service.FlywayMigrationService;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.vfs.VirtualFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Deployment processor that discovers DataSources and creates Flyway migration services.
 * This processor is responsible for:
 * 1. Loading deployment properties from META-INF/flyway.properties
 * 2. Building the complete configuration using FlywayConfigurationBuilder
 * 3. Creating the FlywayMigrationService with the resolved configuration
 */
public class FlywayDeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // Only process top-level deployments
        if (deploymentUnit.getParent() != null) {
            return;
        }

        // Load deployment properties from META-INF/flyway.properties
        Properties deploymentProperties = loadFlywayProperties(deploymentUnit);

        // Explicit opt-out: if the deployment sets flyway.enabled=false (or
        // spring.flyway.enabled=false), skip processing entirely. This avoids
        // running JNDI validation, datasource lookup and service installation
        // for deployments that just happen to ship a db/migration directory.
        if (isExplicitlyDisabled(deploymentProperties)) {
            FlywayLogger.debug("Flyway explicitly disabled for deployment: " + deploymentUnit.getName());
            return;
        }

        // Check if migrations exist or Flyway is explicitly enabled
        if (!hasMigrations(deploymentUnit) && !isExplicitlyEnabled(deploymentProperties)) {
            FlywayLogger.debug("No Flyway migrations found and not explicitly enabled for deployment: " + deploymentUnit.getName());
            return;
        }

        try {
            // Build configuration using the three-tier hierarchy
            FlywayConfigurationBuilder builder = new FlywayConfigurationBuilder(phaseContext, deploymentUnit, deploymentProperties);
            ConfigurationResult config = builder.build();
            
            // Log configuration source for transparency
            String configSource = config.isFromSubsystem() ? "subsystem configuration" : "deployment properties";
            FlywayLogger.infof("Flyway enabled for deployment '%s' using datasource '%s' from %s", 
                             deploymentUnit.getName(), config.getDatasourceJndiName(), configSource);
            
            // Create migration service with the complete configuration
            createMigrationService(phaseContext, deploymentUnit, config);
            
        } catch (Exception e) {
            // If no datasource is configured, log appropriately based on whether migrations exist
            if (hasMigrations(deploymentUnit)) {
                throw new DeploymentUnitProcessingException(
                    "Flyway migrations found but no datasource configured for deployment: " + deploymentUnit.getName() + 
                    ". Please configure a datasource in META-INF/flyway.properties or subsystem configuration.", e);
            } else {
                // Deployment explicitly enabled Flyway but didn't configure datasource
                FlywayLogger.warnf("Flyway explicitly enabled but no datasource configured for deployment: %s", 
                                 deploymentUnit.getName());
            }
        }
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        // Service will be automatically removed by MSC
    }

    /**
     * Load properties from META-INF/flyway.properties in the deployment
     */
    private Properties loadFlywayProperties(DeploymentUnit deploymentUnit) {
        Properties properties = new Properties();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_ROOT);
        final VirtualFile root = deploymentRoot.getRoot();

        try {
            // Try META-INF/flyway.properties first (for JARs and EARs)
            VirtualFile propertiesFile = root.getChild("META-INF/flyway.properties");
            if (propertiesFile.exists()) {
                try (InputStream is = propertiesFile.openStream()) {
                    properties.load(is);
                    FlywayLogger.debugf("Loaded %d properties from META-INF/flyway.properties", properties.size());
                }
            } else {
                // For WARs, also try WEB-INF/classes/META-INF/flyway.properties
                propertiesFile = root.getChild("WEB-INF/classes/META-INF/flyway.properties");
                if (propertiesFile.exists()) {
                    try (InputStream is = propertiesFile.openStream()) {
                        properties.load(is);
                        FlywayLogger.debugf("Loaded %d properties from WEB-INF/classes/META-INF/flyway.properties", properties.size());
                    }
                }
            }
        } catch (IOException e) {
            FlywayLogger.warnf("Failed to load flyway.properties: %s", e.getMessage());
        }

        return properties;
    }

    /**
     * Check if the deployment has migration files.
     * Uses two strategies:
     * 1. ClassLoader resource lookup for standard migration directories
     * 2. VFS-based check on the deployment's resource root as fallback
     */
    private boolean hasMigrations(DeploymentUnit deploymentUnit) {
        // Strategy 1: ClassLoader-based directory check
        ClassLoader classLoader = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.MODULE
        ).getClassLoader();

        String[] locations = {
                "db/migration",
                "WEB-INF/classes/db/migration",
                "META-INF/db/migration"
        };

        for (String location : locations) {
            if (classLoader.getResource(location) != null) {
                return true;
            }
        }

        // Strategy 2: VFS-based check on resource root
        ResourceRoot deploymentRoot = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.DEPLOYMENT_ROOT);
        if (deploymentRoot != null) {
            for (String location : locations) {
                VirtualFile migrationDir = deploymentRoot.getRoot().getChild(location);
                if (migrationDir.exists() && !migrationDir.getChildren().isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if Flyway is explicitly enabled in properties
     */
    private boolean isExplicitlyEnabled(Properties properties) {
        // Check both namespaces
        String enabled = properties.getProperty("flyway.enabled");
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }

        enabled = properties.getProperty("spring.flyway.enabled");
        return "true".equalsIgnoreCase(enabled);
    }

    /**
     * Check if Flyway is explicitly disabled (flyway.enabled=false or
     * spring.flyway.enabled=false) so we can skip processing early.
     */
    private boolean isExplicitlyDisabled(Properties properties) {
        String enabled = properties.getProperty("flyway.enabled");
        if (enabled != null && "false".equalsIgnoreCase(enabled.trim())) {
            return true;
        }
        enabled = properties.getProperty("spring.flyway.enabled");
        return enabled != null && "false".equalsIgnoreCase(enabled.trim());
    }

    /**
     * Create the Flyway migration service
     */
    private void createMigrationService(DeploymentPhaseContext phaseContext,
                                      DeploymentUnit deploymentUnit,
                                      ConfigurationResult config) throws DeploymentUnitProcessingException {

        final ServiceTarget serviceTarget = phaseContext.getRequirementServiceTarget();
        final ServiceName serviceName = deploymentUnit.getServiceName().append("flyway", "migration");

        // Resolve the datasource binder service name from its JNDI name
        final String jndiName = config.getDatasourceJndiName();
        final ServiceName dataSourceServiceName = ContextNames.bindInfoFor(jndiName).getBinderServiceName();
        FlywayLogger.infof("Using datasource service name: %s for JNDI name: %s",
                         dataSourceServiceName, jndiName);

        // Get the deployment classloader
        final ClassLoader deploymentClassLoader = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.MODULE
        ).getClassLoader();

        // Build the service: declare the datasource binder dependency, install the migration service.
        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(serviceName);
        Supplier<ManagedReferenceFactory> dataSourceRefSupplier = serviceBuilder.requires(dataSourceServiceName);

        // Wrap the binder reference into a DataSource supplier
        Supplier<DataSource> dataSourceSupplier = () -> {
            ManagedReferenceFactory factory = dataSourceRefSupplier.get();
            if (factory != null) {
                Object reference = factory.getReference().getInstance();
                if (reference instanceof DataSource) {
                    return (DataSource) reference;
                }
            }
            throw new RuntimeException("Failed to obtain DataSource from reference factory");
        };

        FlywayMigrationService migrationService = new FlywayMigrationService(
                deploymentUnit.getName(),
                dataSourceSupplier,
                deploymentClassLoader,
                config
        );

        serviceBuilder.setInstance(migrationService);
        serviceBuilder.install();

        FlywayLogger.infof("Created Flyway migration service for deployment: %s", deploymentUnit.getName());
    }
}