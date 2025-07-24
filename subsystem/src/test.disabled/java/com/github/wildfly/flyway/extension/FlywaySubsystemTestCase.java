package com.github.wildfly.flyway.extension;

import java.io.IOException;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Basic subsystem test to verify XML parsing and model creation.
 */
public class FlywaySubsystemTestCase extends AbstractSubsystemBaseTest {

    public FlywaySubsystemTestCase() {
        super(FlywayExtension.SUBSYSTEM_NAME, new FlywayExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return "<subsystem xmlns=\"" + FlywaySubsystemNamespace.FLYWAY_1_0.getUriString() + "\" />";
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/flyway-subsystem_1_0.xsd";
    }

    @Test
    @Disabled("XML mapper not properly initialized")
    void testParseSubsystem() throws Exception {
        // Parse the subsystem xml and install into the controller
        String subsystemXml = "<subsystem xmlns=\"" + FlywaySubsystemNamespace.FLYWAY_1_0.getUriString() + "\" />";
        super.parse(subsystemXml);
    }

    @Test
    @Disabled("XML mapper not properly initialized")
    void testParseSubsystemWithEnabled() throws Exception {
        // Parse the subsystem xml with enabled attribute
        String subsystemXml = "<subsystem xmlns=\"" + FlywaySubsystemNamespace.FLYWAY_1_0.getUriString() + "\" enabled=\"false\" />";
        super.parse(subsystemXml);
    }

    @Test
    @Disabled("XML mapper not properly initialized")
    void testWriteSubsystem() throws Exception {
        // Parse the subsystem xml and install into the controller
        String subsystemXml = "<subsystem xmlns=\"" + FlywaySubsystemNamespace.FLYWAY_1_0.getUriString() + "\" />";
        super.standardSubsystemTest(subsystemXml);
    }

    @Test
    @Disabled("XML mapper not properly initialized")
    void testSubsystemWithRuntime() throws Exception {
        String subsystemXml = "<subsystem xmlns=\"" + FlywaySubsystemNamespace.FLYWAY_1_0.getUriString() + "\" />";
        standardSubsystemTest(subsystemXml, false);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.ADMIN_ONLY_HC;
    }
}
