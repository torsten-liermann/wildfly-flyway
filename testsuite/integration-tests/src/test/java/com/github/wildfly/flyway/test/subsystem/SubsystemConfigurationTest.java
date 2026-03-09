package com.github.wildfly.flyway.test.subsystem;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that verifies deployment properties override subsystem configuration.
 *
 * The {@link SubsystemTableSetup} task sets the subsystem attribute
 * {@code table=subsystem_cfg_history} via the management API. The deployment
 * then sets {@code spring.flyway.table=deployment_cfg_history} in its
 * flyway.properties. If the two-tier priority (deployment > subsystem) works
 * correctly, the schema history table must be named
 * {@code deployment_cfg_history}, NOT {@code subsystem_cfg_history}.
 *
 * This test would fail if:
 * <ul>
 *   <li>Deployment properties were silently ignored</li>
 *   <li>Subsystem configuration incorrectly took priority over deployment</li>
 * </ul>
 */
@ExtendWith(ArquillianExtension.class)
@ServerSetup(SubsystemConfigurationTest.SubsystemTableSetup.class)
public class SubsystemConfigurationTest {

    @Deployment
    public static Archive<?> deployment() throws Exception {

        // Create a datasource
        String datasourceXml = """
            <datasources xmlns="urn:jboss:domain:datasources:7.0">
                <datasource jndi-name="java:jboss/datasources/SubsystemTestDS" pool-name="SubsystemTestDS">
                    <driver>h2</driver>
                    <connection-url>jdbc:h2:mem:subsystem-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE</connection-url>
                    <security>
                        <user-name>sa</user-name>
                        <password>sa</password>
                    </security>
                </datasource>
            </datasources>
            """;

        // Migration script
        String migrationSql = """
            CREATE TABLE subsystem_config_test (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                config_source VARCHAR(50) NOT NULL
            );
            INSERT INTO subsystem_config_test (id, name, config_source) VALUES (1, 'test', 'deployment');
            """;

        // Deployment property overrides the subsystem table attribute.
        // The subsystem sets table=subsystem_cfg_history (via ServerSetupTask).
        // This deployment overrides it to deployment_cfg_history.
        String flywayProperties = """
            spring.flyway.enabled=true
            spring.flyway.datasource=java:jboss/datasources/SubsystemTestDS
            spring.flyway.table=deployment_cfg_history
            """;

        return ShrinkWrap.create(WebArchive.class, "subsystem-config-test.war")
            .addAsResource(new StringAsset(migrationSql), "db/migration/V1__Subsystem_config_test.sql")
            .addAsWebInfResource(new StringAsset(datasourceXml), "test-ds.xml")
            .addAsResource(new StringAsset(flywayProperties), "META-INF/flyway.properties")
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testMigrationExecutedSuccessfully() throws Exception {
        // Verify Flyway ran the migration and created expected structures

        Thread.sleep(2000);

        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/SubsystemTestDS");

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<String> tables = getTableNames(metaData);

            // Verify the test table was created
            assertTrue(tables.contains("SUBSYSTEM_CONFIG_TEST"),
                     "Migration should have created subsystem_config_test table");

            // Verify data was inserted
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM subsystem_config_test")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Should have 1 row inserted");
            }

        } catch (Exception e) {
            fail("Failed to verify migration execution: " + e.getMessage());
        }
    }

    @Test
    public void testDeploymentPropertiesOverrideSubsystem() throws Exception {
        // Verify that deployment properties override subsystem defaults.
        //
        // Setup:
        //   Subsystem attribute table = "subsystem_cfg_history" (set by ServerSetupTask)
        //   Deployment property spring.flyway.table = "deployment_cfg_history"
        //
        // Expected:
        //   The actual history table must be "deployment_cfg_history" (deployment wins)
        //   The subsystem table "subsystem_cfg_history" must NOT exist
        //
        // This test would fail if subsystem config took priority over deployment props.

        Thread.sleep(2000);

        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/SubsystemTestDS");

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<String> tables = getTableNames(metaData);

            // The deployment-overridden table must exist
            boolean hasDeploymentTable = tables.contains("DEPLOYMENT_CFG_HISTORY")
                    || tables.contains("deployment_cfg_history");
            assertTrue(hasDeploymentTable,
                    "Schema history table should use the deployment property value " +
                    "(spring.flyway.table=deployment_cfg_history), " +
                    "proving deployment properties override subsystem config; " +
                    "actual tables: " + tables);

            // The subsystem-configured table must NOT exist
            assertFalse(tables.contains("SUBSYSTEM_CFG_HISTORY")
                        || tables.contains("subsystem_cfg_history"),
                    "Subsystem-configured table name (subsystem_cfg_history) should NOT exist " +
                    "when deployment properties override subsystem config; " +
                    "actual tables: " + tables);

            // The Flyway hardcoded default should also not exist
            assertFalse(tables.contains("FLYWAY_SCHEMA_HISTORY")
                        || tables.contains("flyway_schema_history"),
                    "Default Flyway schema history table should NOT exist " +
                    "when deployment properties set a custom table name; " +
                    "actual tables: " + tables);

            // Verify migration was recorded in the overridden table
            String historyTable = tables.contains("DEPLOYMENT_CFG_HISTORY")
                    ? "DEPLOYMENT_CFG_HISTORY" : "deployment_cfg_history";
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM \"" + historyTable + "\" WHERE \"version\" = '1'")) {
                assertTrue(rs.next(), "History table should have entries");
                assertTrue(rs.getInt(1) > 0,
                        "Migration V1 should be recorded in the deployment-overridden history table");
            }
        }
    }

    private List<String> getTableNames(DatabaseMetaData metaData) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    /**
     * Sets the subsystem {@code table} attribute to {@code subsystem_cfg_history}
     * and reloads the server so that {@link com.github.wildfly.flyway.config.SubsystemConfigurationHolder}
     * is populated with the new value during boot.
     *
     * Without the reload, the attribute change would only be persisted in the
     * management model but not visible to {@code FlywayConfigurationBuilder},
     * because the holder is populated in the {@code SubsystemAdd} handler
     * which only runs during the boot phase.
     */
    static class SubsystemTableSetup implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            // Set subsystem table attribute to a non-default value
            ModelNode writeOp = new ModelNode();
            writeOp.get("operation").set("write-attribute");
            writeOp.get("address").add("subsystem", "flyway");
            writeOp.get("name").set("table");
            writeOp.get("value").set("subsystem_cfg_history");

            ModelNode result = managementClient.getControllerClient().execute(writeOp);
            assertEquals("success", result.get("outcome").asString(),
                    "Setting subsystem table attribute should succeed; result: " + result);

            // Reload the server so SubsystemConfigurationHolder picks up the new value.
            // The ReloadRequiredWriteAttributeHandler marks the server as reload-required;
            // an actual reload re-runs SubsystemAdd.performBoottime() which populates
            // the holder with the updated attribute values.
            reloadServer(managementClient);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            // Reset to default
            ModelNode undefineOp = new ModelNode();
            undefineOp.get("operation").set("undefine-attribute");
            undefineOp.get("address").add("subsystem", "flyway");
            undefineOp.get("name").set("table");

            managementClient.getControllerClient().execute(undefineOp);

            // Reload to restore default SubsystemConfigurationHolder state
            reloadServer(managementClient);
        }

        private void reloadServer(ManagementClient managementClient) throws Exception {
            ModelNode reloadOp = new ModelNode();
            reloadOp.get("operation").set("reload");

            managementClient.getControllerClient().execute(reloadOp);

            // Wait for the server to complete the reload
            long deadline = System.currentTimeMillis() + 30_000;
            boolean running = false;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
                try {
                    ModelNode stateOp = new ModelNode();
                    stateOp.get("operation").set("read-attribute");
                    stateOp.get("name").set("server-state");
                    ModelNode stateResult = managementClient.getControllerClient().execute(stateOp);
                    if ("success".equals(stateResult.get("outcome").asString())
                            && "running".equals(stateResult.get("result").asString())) {
                        running = true;
                        break;
                    }
                } catch (Exception ignored) {
                    // Server may not be reachable during reload — retry
                }
            }
            assertTrue(running, "Server should be running after reload");
        }
    }
}
