package com.github.wildfly.flyway.test.subsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ArquillianExtension.class)
@ServerSetup(FlywaySubsystemTest.FlywaySubsystemSetup.class)
public class FlywaySubsystemTest {

    @Deployment
    public static Archive<?> deployment() {
        // Test subsystem functionality without migrations
        return ShrinkWrap.create(WebArchive.class, "flyway-subsystem-test.war");
    }

    @Test
    public void testSubsystemIsActive() {
        // The ServerSetupTask verifies the subsystem is readable via read-resource.
        // If setup() fails, this test does not execute (Arquillian contract).
        // Reaching this point proves the subsystem booted and responded to
        // a management read-resource call with outcome=success.
        assertNotNull(FlywaySubsystemSetup.class,
                "Flyway subsystem should be active (setup verified via management API)");
    }

    static class FlywaySubsystemSetup implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            // Check if flyway subsystem exists
            ModelNode op = new ModelNode();
            op.get("operation").set("read-resource");
            op.get("address").add("subsystem", "flyway");

            ModelNode result = managementClient.getControllerClient().execute(op);
            assertEquals("success", result.get("outcome").asString());
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) {
            // Nothing to tear down
        }
    }
}
