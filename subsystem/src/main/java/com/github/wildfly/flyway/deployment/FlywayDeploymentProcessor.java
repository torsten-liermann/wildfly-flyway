package com.github.wildfly.flyway.deployment;

import com.github.wildfly.flyway.logging.FlywayLogger;
import com.github.wildfly.flyway.service.FlywayMigrationService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Deployment processor that discovers DataSources and creates Flyway migration services.
 * No CDI dependencies - pure WildFly deployment processing.
 */
public class FlywayDeploymentProcessor implements DeploymentUnitProcessor {
    
    
    public static final Phase PHASE = Phase.POST_MODULE;
    public static final int PRIORITY = 0x3800 + 0x100;
    
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
        var resourceRoot = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        if (resourceRoot == null || resourceRoot.getClassLoader() == null) {
            return false;
        }
        
        // Check classpath for migration resources
        return resourceRoot.getClassLoader().getResource("db/migration") != null ||  
               resourceRoot.getClassLoader().getResource("WEB-INF/classes/db/migration") != null ||  
               resourceRoot.getClassLoader().getResource("META-INF/db/migration") != null;
    }
    
    /**
     * Find the DataSource JNDI name to use.
     * Priority:
     * 1. spring.flyway.datasource property
     * 2. spring.flyway.url property (create datasource)
     * 3. Default datasource
     */
    private String findDataSourceJndiName(DeploymentUnit deploymentUnit) {
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
        
        // DataSource supplier
        ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(dataSourceJndiName);
        Supplier<DataSource> dataSourceSupplier = serviceBuilder.requires(bindInfo.getBinderServiceName());
        
        // PathManager supplier
        Supplier<PathManager> pathManagerSupplier = serviceBuilder.requires(
                support.getCapabilityServiceName(PATH_MANAGER_CAPABILITY));
        
        // Consumer for service reference
        Consumer<FlywayMigrationService> serviceConsumer = serviceBuilder.provides(serviceName);
        
        // Create and install service using modern Service pattern
        FlywayMigrationService serviceInstance = new FlywayMigrationService(
                deploymentUnit.getName(),
                serviceConsumer,
                dataSourceSupplier,
                pathManagerSupplier);
        
        serviceBuilder.setInstance(Service.newInstance(serviceConsumer, serviceInstance));
        serviceBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        serviceBuilder.install();
        
        FlywayLogger.infof("Flyway migration service created for deployment: %s with datasource: %s", 
                deploymentUnit.getName(), dataSourceJndiName);
    }
}
