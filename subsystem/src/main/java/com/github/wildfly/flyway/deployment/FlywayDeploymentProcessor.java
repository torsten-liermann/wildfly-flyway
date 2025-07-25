package com.github.wildfly.flyway.deployment;

import com.github.wildfly.flyway.logging.FlywayLogger;
import com.github.wildfly.flyway.service.FlywayMigrationService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment processor that discovers DataSources and creates Flyway migration services.
 * No CDI dependencies - pure WildFly deployment processing.
 */
public class FlywayDeploymentProcessor implements DeploymentUnitProcessor {
    
    
    public static final Phase PHASE = Phase.POST_MODULE;
    public static final int PRIORITY = 0x3800 + 0x100;
    
    private static final String DEFAULT_DATASOURCE_JNDI_NAME = "java:jboss/datasources/ExampleDS";
    private String configuredDefaultDatasource = null;
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
        if ("true".equalsIgnoreCase(System.getProperty("spring.flyway.enabled"))) {
            FlywayLogger.debugf("Flyway enabled via system property for deployment: %s", deploymentUnit.getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Find the DataSource JNDI name to use.
     * Priority:
     * 1. spring.flyway.datasource property
     * 2. spring.flyway.url property (create datasource)
     * 3. Default datasource
     */
    private String findDataSourceJndiName(DeploymentUnit deploymentUnit) {
        // Try to read from META-INF/flyway-test.properties
        ResourceRoot root = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_ROOT);
        if (root != null) {
            VirtualFile propertiesFile = root.getRoot().getChild("META-INF/flyway-test.properties");
            if (propertiesFile.exists()) {
                try (InputStream is = propertiesFile.openStream()) {
                    Properties props = new Properties();
                    props.load(is);
                    String datasource = props.getProperty("spring.flyway.datasource");
                    if (datasource != null && !datasource.trim().isEmpty()) {
                        FlywayLogger.infof("Using datasource from properties: %s", datasource);
                        return datasource.trim();
                    }
                } catch (IOException e) {
                    FlywayLogger.warnf("Failed to read flyway-test.properties: %s", e.getMessage());
                }
            }
        }
        
        // Check for explicit datasource property
        String datasourceProperty = System.getProperty("spring.flyway.datasource");
        if (datasourceProperty != null) {
            return datasourceProperty;
        }
        
        // Check for JDBC URL (would need to create datasource - not implemented yet)
        String jdbcUrl = System.getProperty("spring.flyway.url");
        if (jdbcUrl != null) {
            FlywayLogger.warn("spring.flyway.url is set but direct JDBC connections are not yet supported. Using default datasource.");
        }
        
        // Check deployment for @Resource annotations or persistence.xml
        EEModuleDescription moduleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        if (moduleDescription != null) {
            // Could analyze for datasource references here
        }
        
        // Use default
        return DEFAULT_DATASOURCE_JNDI_NAME;
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
