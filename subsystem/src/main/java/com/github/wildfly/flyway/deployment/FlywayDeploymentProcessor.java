package com.github.wildfly.flyway.deployment;

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
import org.jboss.metadata.property.PropertyReplacer;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VirtualFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deployment processor that discovers DataSources and creates Flyway migration services.
 * No CDI dependencies - pure WildFly deployment processing.
 */
public class FlywayDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String DEFAULT_DATASOURCE_JNDI_NAME = "java:jboss/datasources/ExampleDS";
    private static final String PATH_MANAGER_CAPABILITY = "org.wildfly.management.path-manager";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // Only process top-level deployments
        if (deploymentUnit.getParent() != null) {
            return;
        }

        // Check if migrations exist
        if (!hasMigrations(deploymentUnit)) {
            FlywayLogger.debug("No Flyway migrations found in deployment: " + deploymentUnit.getName());
            return;
        }

        FlywayLogger.infof("Flyway migrations detected in deployment: %s", deploymentUnit.getName());

        // Find DataSource
        String dataSourceJndiName = findDataSourceJndiName(deploymentUnit);

        // Create migration service
        createMigrationService(phaseContext, deploymentUnit, dataSourceJndiName);
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        // Service will be automatically removed
    }

    /**
     * Check if deployment contains Flyway migrations.
     */
    private boolean hasMigrations(DeploymentUnit deploymentUnit) {
        // Check for standard migration locations
        var module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        if (module == null || module.getClassLoader() == null) {
            return false;
        }

        ClassLoader classLoader = module.getClassLoader();

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

        // For WAR deployments, always check if Flyway is enabled via system property
        // Support both spring.flyway.enabled and flyway.enabled
        if ("true".equalsIgnoreCase(System.getProperty("spring.flyway.enabled")) ||
            "true".equalsIgnoreCase(System.getProperty("flyway.enabled"))) {
            FlywayLogger.debugf("Flyway enabled via system property for deployment: %s", deploymentUnit.getName());
            return true;
        }

        return false;
    }

    /**
     * Find the DataSource JNDI name to use.
     * Priority:
     * 1. META-INF/flyway.properties in deployment
     * 2. System properties
     * 3. Default datasource from subsystem
     */
    private String findDataSourceJndiName(DeploymentUnit deploymentUnit) {
        // Load deployment-specific properties
        Properties flywayProperties = loadFlywayProperties(deploymentUnit);

        // Check deployment properties first
        // Support both spring.flyway.datasource and flyway.datasource
        String datasource = flywayProperties.getProperty("spring.flyway.datasource");
        if (datasource == null || datasource.trim().isEmpty()) {
            datasource = flywayProperties.getProperty("flyway.datasource");
        }
        if (datasource != null && !datasource.trim().isEmpty()) {
            // Resolve WildFly expressions
            datasource = resolveExpression(deploymentUnit, datasource);
            FlywayLogger.infof("Using datasource from META-INF/flyway.properties: %s", datasource);
            return datasource;
        }

        // Check system properties - support both prefixes
        datasource = System.getProperty("spring.flyway.datasource");
        if (datasource == null || datasource.trim().isEmpty()) {
            datasource = System.getProperty("flyway.datasource");
        }
        if (datasource != null && !datasource.trim().isEmpty()) {
            FlywayLogger.infof("Using datasource from system property: %s", datasource);
            return datasource;
        }

        // Use default
        FlywayLogger.infof("Using default datasource: %s", DEFAULT_DATASOURCE_JNDI_NAME);
        return DEFAULT_DATASOURCE_JNDI_NAME;
    }

    /**
     * Load Flyway properties from META-INF/flyway.properties
     */
    private Properties loadFlywayProperties(DeploymentUnit deploymentUnit) {
        Properties properties = new Properties();

        ResourceRoot root = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_ROOT);
        if (root != null) {
            // Try multiple locations for different deployment types
            String[] possibleLocations = {
                "META-INF/flyway.properties",
                "WEB-INF/classes/META-INF/flyway.properties",
                "WEB-INF/flyway.properties"
            };
            
            VirtualFile propertiesFile = null;
            for (String location : possibleLocations) {
                VirtualFile candidate = root.getRoot().getChild(location);
                if (candidate.exists()) {
                    propertiesFile = candidate;
                    FlywayLogger.infof("Found flyway.properties at: %s", location);
                    break;
                }
            }
            
            if (propertiesFile != null && propertiesFile.exists()) {
                try (InputStream is = propertiesFile.openStream()) {
                    properties.load(is);
                    FlywayLogger.infof("Loaded %d properties from %s", properties.size(), propertiesFile.getPathName());
                    // Log the properties for debugging
                    for (String key : properties.stringPropertyNames()) {
                        FlywayLogger.debugf("  Property: %s = %s", key, properties.getProperty(key));
                    }
                } catch (IOException e) {
                    FlywayLogger.warnf("Failed to load flyway.properties: %s", e.getMessage());
                }
            } else {
                FlywayLogger.infof("No flyway.properties found in deployment: %s", deploymentUnit.getName());
            }
        } else {
            FlywayLogger.debugf("No deployment root found for: %s", deploymentUnit.getName());
        }

        return properties;
    }

    /**
     * Resolve WildFly expressions in property values
     */
    private String resolveExpression(DeploymentUnit deploymentUnit, String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        // Use the PropertyReplacer attached by DeploymentPropertyResolverProcessor
        final PropertyReplacer propertyReplacer = deploymentUnit.getAttachment(
                org.jboss.as.ee.metadata.property.Attachments.FINAL_PROPERTY_REPLACER);
        
        if (propertyReplacer != null) {
            return propertyReplacer.replaceProperties(value);
        } else {
            // Fallback to simple system property resolution
            return resolveSystemProperties(value);
        }
    }

    /**
     * Simple fallback for resolving system properties
     */
    private String resolveSystemProperties(String value) {
        Pattern pattern = Pattern.compile("\\$\\{([^:}]+)(?::([^}]+))?}");
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String replacement = System.getProperty(key);

            if (replacement == null) {
                replacement = defaultValue != null ? defaultValue : matcher.group(0);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Create the Flyway migration service.
     */
    private void createMigrationService(DeploymentPhaseContext phaseContext,
                                        DeploymentUnit deploymentUnit,
                                        String dataSourceJndiName) {

        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        final ServiceName serviceName = deploymentUnit.getServiceName()
                .append("flyway", "migration");

        // Get capability support
        CapabilityServiceSupport support = deploymentUnit.getAttachment(
                org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT);

        // Create service
        final ServiceBuilder<?> serviceBuilder = serviceTarget.addService(serviceName);

        // DataSource injection - use proper dependency injection with ManagedReferenceFactory
        ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(dataSourceJndiName);
        ServiceName dataSourceServiceName = bindInfo.getBinderServiceName();

        // Add dependency for ManagedReferenceFactory
        Supplier<ManagedReferenceFactory> referenceFactorySupplier = serviceBuilder.requires(dataSourceServiceName);

        // Create supplier that resolves the DataSource from the ManagedReferenceFactory
        Supplier<DataSource> dataSourceSupplier = () -> {
            try {
                ManagedReferenceFactory factory = referenceFactorySupplier.get();
                return (DataSource) factory.getReference().getInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to get DataSource from ManagedReferenceFactory", e);
            }
        };

        // PathManager supplier
        Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(
                support.getCapabilityServiceName(PATH_MANAGER_CAPABILITY));

        // Consumer for service reference
        Consumer<FlywayMigrationService> serviceConsumer = serviceBuilder.provides(serviceName);

        // Get deployment classloader
        var module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        ClassLoader deploymentClassLoader = module != null ? module.getClassLoader() : null;

        // Create and install service using modern Service pattern with lifecycle
        FlywayMigrationService serviceInstance = new FlywayMigrationService(
                deploymentUnit.getName(),
                serviceConsumer,
                dataSourceSupplier,
                pathManagerSupplier,
                deploymentClassLoader);

        // Use Service.newInstance with lifecycle hooks
        Service service = Service.newInstance(serviceConsumer, serviceInstance);
        serviceBuilder.setInstance(new Service() {
            @Override
            public void start(StartContext context) throws StartException {
                try {
                    serviceInstance.start(context);
                    service.start(context);
                } catch (org.jboss.msc.service.StartException e) {
                    throw e;
                } catch (Exception e) {
                    throw new StartException(e.getMessage(), e);
                }
            }

            @Override
            public void stop(StopContext context) {
                serviceInstance.stop(context);
                service.stop(context);
            }
        });
        serviceBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        serviceBuilder.install();

        FlywayLogger.infof("Flyway migration service created for deployment: %s with datasource: %s",
                deploymentUnit.getName(), dataSourceJndiName);
    }
}
