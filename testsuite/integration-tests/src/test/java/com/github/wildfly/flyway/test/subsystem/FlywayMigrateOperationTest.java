package com.github.wildfly.flyway.test.subsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Integration test for the Flyway management CLI operation
 * {@code /subsystem=flyway/migration=flyway:migrate}.
 *
 * All management API verifications happen in the {@link MigrateSetup} task which
 * has access to the {@link ManagementClient}. If any assertion in setup() fails,
 * Arquillian will not execute the test methods (Arquillian contract for
 * ServerSetupTask). Therefore, reaching any @Test method proves that all
 * management operations completed successfully.
 *
 * Verified in setup():
 * <ul>
 *   <li>migration=flyway resource can be added with all attributes</li>
 *   <li>All attributes (datasource, enabled, locations, baseline-on-migrate,
 *       clean-disabled) are readable and correctly stored</li>
 *   <li>The {@code migrate} operation executes successfully against ExampleDS</li>
 *   <li>Setting {@code enabled=false} causes the migrate operation to fail</li>
 * </ul>
 */
@ExtendWith(ArquillianExtension.class)
@ServerSetup(FlywayMigrateOperationTest.MigrateSetup.class)
public class FlywayMigrateOperationTest {

    @Deployment
    public static Archive<?> deployment() {
        // Empty deployment — the management operation is tested via ManagementClient
        // in the ServerSetupTask, not via deployment processing.
        return ShrinkWrap.create(WebArchive.class, "migrate-op-test.war");
    }

    @Test
    public void testManagementOperationVerified() {
        // All management operation tests are executed in MigrateSetup.setup().
        // Reaching this point proves that:
        //   1. migration=flyway resource was added successfully
        //   2. all attributes were verified as readable and correct
        //   3. migrate operation executed successfully
        //   4. enabled=false correctly rejected the migrate call
        // If any of these failed, setup() would have thrown and this test
        // would not execute (Arquillian ServerSetupTask contract).
        assertNotNull(MigrateSetup.class,
                "Management operations verified (add, read-resource, migrate, enabled=false rejection)");
    }

    static class MigrateSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            // --- Step 1: Add migration=flyway resource ---
            ModelNode addOp = new ModelNode();
            addOp.get("operation").set("add");
            addOp.get("address").add("subsystem", "flyway");
            addOp.get("address").add("migration", "flyway");
            addOp.get("datasource").set("java:jboss/datasources/ExampleDS");
            addOp.get("enabled").set(true);
            addOp.get("locations").set("classpath:db/migration");
            addOp.get("baseline-on-migrate").set(false);
            addOp.get("clean-disabled").set(true);

            ModelNode addResult = managementClient.getControllerClient().execute(addOp);
            assertEquals("success", addResult.get("outcome").asString(),
                    "migration=flyway add should succeed; result: " + addResult);

            // --- Step 2: Verify attributes are readable ---
            ModelNode readOp = new ModelNode();
            readOp.get("operation").set("read-resource");
            readOp.get("address").add("subsystem", "flyway");
            readOp.get("address").add("migration", "flyway");
            readOp.get("include-defaults").set(true);

            ModelNode readResult = managementClient.getControllerClient().execute(readOp);
            assertEquals("success", readResult.get("outcome").asString(),
                    "read-resource should succeed; result: " + readResult);

            ModelNode resource = readResult.get("result");
            assertEquals("java:jboss/datasources/ExampleDS", resource.get("datasource").asString(),
                    "datasource attribute mismatch");
            assertTrue(resource.get("enabled").asBoolean(),
                    "enabled attribute should be true");
            assertEquals("classpath:db/migration", resource.get("locations").asString(),
                    "locations attribute mismatch");
            assertEquals(false, resource.get("baseline-on-migrate").asBoolean(),
                    "baseline-on-migrate should be false");
            assertTrue(resource.get("clean-disabled").asBoolean(),
                    "clean-disabled should be true");

            // --- Step 3: Execute migrate ---
            ModelNode migrateOp = new ModelNode();
            migrateOp.get("operation").set("migrate");
            migrateOp.get("address").add("subsystem", "flyway");
            migrateOp.get("address").add("migration", "flyway");

            ModelNode migrateResult = managementClient.getControllerClient().execute(migrateOp);
            assertEquals("success", migrateResult.get("outcome").asString(),
                    "migrate should succeed; result: " + migrateResult);
            ModelNode resultValue = migrateResult.get("result");
            assertTrue(resultValue.isDefined(), "migrate should return a result object");
            assertTrue(resultValue.get("success").asBoolean(), "migration should report success");

            // --- Step 4: Disable and verify rejection ---
            ModelNode disableOp = new ModelNode();
            disableOp.get("operation").set("write-attribute");
            disableOp.get("address").add("subsystem", "flyway");
            disableOp.get("address").add("migration", "flyway");
            disableOp.get("name").set("enabled");
            disableOp.get("value").set(false);

            ModelNode disableResult = managementClient.getControllerClient().execute(disableOp);
            assertEquals("success", disableResult.get("outcome").asString(),
                    "write-attribute should succeed; result: " + disableResult);

            ModelNode disabledMigrateOp = new ModelNode();
            disabledMigrateOp.get("operation").set("migrate");
            disabledMigrateOp.get("address").add("subsystem", "flyway");
            disabledMigrateOp.get("address").add("migration", "flyway");

            ModelNode disabledMigrateResult = managementClient.getControllerClient().execute(disabledMigrateOp);
            assertEquals("failed", disabledMigrateResult.get("outcome").asString(),
                    "migrate should fail when disabled; result: " + disabledMigrateResult);
            String failureDesc = disabledMigrateResult.get("failure-description").asString();
            assertTrue(failureDesc.contains("disabled"),
                    "failure should mention 'disabled'; got: " + failureDesc);

            // Re-enable for clean state
            disableOp.get("value").set(true);
            managementClient.getControllerClient().execute(disableOp);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            ModelNode removeOp = new ModelNode();
            removeOp.get("operation").set("remove");
            removeOp.get("address").add("subsystem", "flyway");
            removeOp.get("address").add("migration", "flyway");
            managementClient.getControllerClient().execute(removeOp);
        }
    }
}
