package com.github.wildfly.flyway.deployment;

import com.github.wildfly.flyway.config.FlywayConfigurationBuilder;
import com.github.wildfly.flyway.config.FlywayConfigurationBuilder.ConfigurationResult;
import com.github.wildfly.flyway.logging.FlywayLogger;
import com.github.wildfly.flyway.service.FlywayMigrationService;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VirtualFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Deployment processor that discovers DataSources and creates Flyway migration services.
 * This processor is responsible for:
 * 1. Loading deployment properties from META-INF/flyway.properties
 * 2. Building the complete configuration using FlywayConfigurationBuilder
 * 3. Creating the FlywayMigrationService with the resolved configuration
 */
public class FlywayDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String PATH_MANAGER_CAPABILITY = "org.wildfly.management.path-manager";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // Only process top-level deployments
        if (deploymentUnit.getParent() != null) {
            return;
        }

        // Load deployment properties from META-INF/flyway.properties
        Properties deploymentProperties = loadFlywayProperties(deploymentUnit);
        
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
     * Check if the deployment has migration files
     */
    private boolean hasMigrations(DeploymentUnit deploymentUnit) {
        ClassLoader classLoader = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.MODULE
        ).getClassLoader();

        // Check for any SQL migration files in standard locations
        String[] locations = {
                "db/migration",
                "WEB-INF/classes/db/migration",
                "META-INF/db/migration"
        };

        for (String location : locations) {
            // Check if directory exists
            if (classLoader.getResource(location) != null) {
                return true;
            }

            // Also check for specific migration files (V*.sql, U*.sql, R*.sql)
            String[] prefixes = {"V", "U", "R"};
            for (String prefix : prefixes) {
                if (classLoader.getResource(location + "/" + prefix + "1__") != null ||
                        classLoader.getResource(location + "/" + prefix + "001__") != null ||
                        classLoader.getResource(location + "/" + prefix + "1.0__") != null ||
                        classLoader.getResource(location + "/" + prefix + "1_") != null) {
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
     * Create the Flyway migration service
     */
    private void createMigrationService(DeploymentPhaseContext phaseContext, 
                                      DeploymentUnit deploymentUnit,
                                      ConfigurationResult config) throws DeploymentUnitProcessingException {
        
        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        final ServiceName serviceName = deploymentUnit.getServiceName().append("flyway", "migration");

        // Get capability support
        CapabilityServiceSupport capabilitySupport = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT
        );

        // Get the datasource service name
        // Use capability to get the proper service name for the datasource
        String jndiName = config.getDatasourceJndiName();
        ServiceName dataSourceServiceName;
        
        // For datasources deployed with the application, we need to use the bind info
        // For global datasources, we can use the capability
        String dsName = extractDataSourceName(jndiName);
        
        // First try the bind info approach (works for deployment-specific datasources)
        ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
        dataSourceServiceName = bindInfo.getBinderServiceName();
        
        FlywayLogger.infof("Using datasource service name: %s for JNDI name: %s", 
                         dataSourceServiceName, jndiName);

        // Get the classloader
        ClassLoader deploymentClassLoader = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.MODULE
        ).getClassLoader();

        // Create wrapper service for WildFly 28 compatibility
        final FlywayMigrationService[] serviceHolder = new FlywayMigrationService[1];
        Service<Void> wrapperService = new Service<Void>() {
            @Override
            public void start(StartContext context) throws StartException {
                serviceHolder[0].start(context);
            }

            @Override
            public void stop(StopContext context) {
                if (serviceHolder[0] != null) {
                    serviceHolder[0].stop(context);
                }
            }
            
            @Override
            public Void getValue() throws IllegalStateException, IllegalArgumentException {
                return null;
            }
        };

        // Build the service
        ServiceBuilder<?> serviceBuilder = serviceTarget.addService(serviceName, wrapperService);

        // Add capability requirements
        serviceBuilder.requires(capabilitySupport.getCapabilityServiceName(PATH_MANAGER_CAPABILITY));

        // Create suppliers and consumers
        Consumer<FlywayMigrationService> serviceConsumer = service -> {
            serviceHolder[0] = service;
        };

        // Add a dependency on the datasource's reference factory service
        // The binder service name already provides access to the ManagedReferenceFactory
        Supplier<ManagedReferenceFactory> dataSourceRefSupplier = serviceBuilder.requires(dataSourceServiceName);
        
        Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(
                capabilitySupport.getCapabilityServiceName(PATH_MANAGER_CAPABILITY)
        );

        // Create a wrapper supplier that extracts the DataSource from the reference
        Supplier<DataSource> dataSourceSupplier = () -> {
            try {
                ManagedReferenceFactory factory = dataSourceRefSupplier.get();
                if (factory != null) {
                    Object reference = factory.getReference().getInstance();
                    if (reference instanceof DataSource) {
                        return (DataSource) reference;
                    }
                }
                throw new RuntimeException("Failed to obtain DataSource from reference factory");
            } catch (Exception e) {
                throw new RuntimeException("Failed to get DataSource reference", e);
            }
        };
        
        // Create the migration service with complete configuration
        FlywayMigrationService migrationService = new FlywayMigrationService(
                deploymentUnit.getName(),
                serviceConsumer,
                dataSourceSupplier,
                pathManagerSupplier,
                deploymentClassLoader,
                config
        );

        // Set initial reference
        serviceHolder[0] = migrationService;

        // Install the service
        serviceBuilder.install();

        FlywayLogger.infof("Created Flyway migration service for deployment: %s", deploymentUnit.getName());
    }
    
    /**
     * Extract the datasource name from a JNDI name.
     * For example: "java:jboss/datasources/ExampleDS" -> "ExampleDS"
     */
    private String extractDataSourceName(String jndiName) {
        // Remove common prefixes
        if (jndiName.startsWith("java:jboss/datasources/")) {
            return jndiName.substring("java:jboss/datasources/".length());
        } else if (jndiName.startsWith("java:/")) {
            return jndiName.substring("java:/".length());
        } else if (jndiName.contains("/")) {
            return jndiName.substring(jndiName.lastIndexOf('/') + 1);
        }
        return jndiName;
    }
}