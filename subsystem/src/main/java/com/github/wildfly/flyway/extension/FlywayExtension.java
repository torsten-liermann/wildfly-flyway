package com.github.wildfly.flyway.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;

/**
 * The Flyway extension - Spring Boot style migration for WildFly.
 */
public class FlywayExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "flyway";
    
    private static final String RESOURCE_NAME = FlywayExtension.class.getPackage().getName() + ".LocalDescriptions";
    
    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(1, 0, 0);
    
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    
    static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), 
                RESOURCE_NAME, 
                FlywayExtension.class.getClassLoader(), 
                true, 
                false);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, 
                FlywaySubsystemNamespace.FLYWAY_1_0.getUriString(), 
                FlywaySubsystemParser.INSTANCE);
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(
                SUBSYSTEM_NAME, 
                CURRENT_MODEL_VERSION);
        
        subsystem.registerSubsystemModel(new FlywaySubsystemDefinition());
        subsystem.registerXMLElementWriter(FlywaySubsystemWriter.INSTANCE);
    }
}
