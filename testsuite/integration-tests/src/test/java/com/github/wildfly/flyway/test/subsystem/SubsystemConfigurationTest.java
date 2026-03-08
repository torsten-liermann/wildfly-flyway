package com.github.wildfly.flyway.test.subsystem;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
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
 * Test that verifies deployment properties are applied to Flyway configuration.
 *
 * This test specifically validates that:
 * 1. Flyway properties from META-INF/flyway.properties are applied
 * 2. The schema history table name can be overridden via deployment properties
 * 3. The datasource from deployment properties is used
 */
@ExtendWith(ArquillianExtension.class)
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
            INSERT INTO subsystem_config_test (id, name, config_source) VALUES (1, 'test', 'subsystem');
            """;

        // Deployment properties override the default schema history table name.
        // The Flyway default is "flyway_schema_history". By setting a different name
        // here, we can verify that deployment properties are actually applied to
        // the Flyway configuration — the history table must use the overridden name.
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
    public void testDeploymentPropertiesOverrideDefaults() throws Exception {
        // Verify that deployment properties actually affect Flyway configuration.
        //
        // The deployment sets spring.flyway.table=deployment_cfg_history, overriding
        // the Flyway default "flyway_schema_history". If this property is correctly
        // applied, the schema history table must be named DEPLOYMENT_CFG_HISTORY
        // (H2 uppercases by default), and the default FLYWAY_SCHEMA_HISTORY must
        // NOT exist. This test would fail if deployment properties were silently
        // ignored.

        Thread.sleep(2000);

        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/SubsystemTestDS");

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<String> tables = getTableNames(metaData);

            // The overridden history table must exist (Flyway writes the name as-is,
            // so it may be lowercase or uppercase depending on the database)
            boolean hasOverriddenTable = tables.contains("DEPLOYMENT_CFG_HISTORY")
                    || tables.contains("deployment_cfg_history");
            assertTrue(hasOverriddenTable,
                    "Schema history table should use the name from deployment properties " +
                    "(spring.flyway.table=deployment_cfg_history); actual tables: " + tables);

            // The Flyway default table name must NOT exist — proves the override took effect
            assertFalse(tables.contains("FLYWAY_SCHEMA_HISTORY")
                        || tables.contains("flyway_schema_history"),
                    "Default Flyway schema history table should NOT exist when " +
                    "deployment properties override the table name; actual tables: " + tables);

            // Verify migration was recorded in the overridden table
            String historyTable = tables.contains("DEPLOYMENT_CFG_HISTORY")
                    ? "DEPLOYMENT_CFG_HISTORY" : "deployment_cfg_history";
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM \"" + historyTable + "\" WHERE \"version\" = '1'")) {
                assertTrue(rs.next(), "History table should have entries");
                assertTrue(rs.getInt(1) > 0,
                        "Migration V1 should be recorded in the overridden history table");
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
}
