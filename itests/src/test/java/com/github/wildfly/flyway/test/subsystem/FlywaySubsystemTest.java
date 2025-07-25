package com.github.wildfly.flyway.test.subsystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@ServerSetup(FlywaySubsystemTest.FlywaySubsystemSetup.class)
public class FlywaySubsystemTest {

    @Deployment
    public static Archive<?> deployment() {
        // Test subsystem functionality without migrations
        return ShrinkWrap.create(WebArchive.class, "flyway-subsystem-test.war");
    }

    @Test
    public void testSubsystemIsActive() throws Exception {
        // Test will pass if subsystem is active
        assertTrue("Flyway subsystem should be active", true);
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
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            // Nothing to tear down
        }
    }
}
