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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that verifies environment variable expression resolution in deployment properties.
 * 
 * This test demonstrates that:
 * 1. Environment variable expressions like ${env.VARIABLE_NAME} are resolved in flyway.properties
 * 2. Default values can be provided using ${env.VARIABLE_NAME:defaultValue}
 * 3. The WildFly expression resolver handles system properties and environment variables
 * 
 * In a cloud deployment, you can set environment variables in your container/kubernetes
 * configuration and reference them in your flyway.properties file.
 */
@ExtendWith(ArquillianExtension.class)
public class EnvironmentVariableConfigurationTest {

    @Deployment
    public static Archive<?> deployment() throws Exception {
        
        // Create a datasource
        String datasourceXml = """
            <datasources xmlns="urn:jboss:domain:datasources:7.0">
                <datasource jndi-name="java:jboss/datasources/EnvVarTestDS" pool-name="EnvVarTestDS">
                    <driver>h2</driver>
                    <connection-url>jdbc:h2:mem:envvar-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE</connection-url>
                    <security>
                        <user-name>sa</user-name>
                        <password>sa</password>
                    </security>
                </datasource>
            </datasources>
            """;
        
        // Migration script
        String migrationSql = """
            CREATE TABLE env_var_test (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                config_type VARCHAR(50) NOT NULL
            );
            INSERT INTO env_var_test (id, name, config_type) VALUES (1, 'test', 'environment-variable');
            """;
        
        // Configuration using environment variable expressions
        // These will be resolved by the WildFly expression resolver
        String flywayProperties = """
            spring.flyway.enabled=${env.FLYWAY_ENABLED:true}
            spring.flyway.datasource=${env.FLYWAY_DATASOURCE:java:jboss/datasources/EnvVarTestDS}
            spring.flyway.baseline-on-migrate=${env.FLYWAY_BASELINE_ON_MIGRATE:false}
            spring.flyway.locations=${env.FLYWAY_LOCATIONS:classpath:db/migration}
            """;
        
        return ShrinkWrap.create(WebArchive.class, "envvar-test.war")
            .addAsResource(new StringAsset(migrationSql), "db/migration/V1__Env_var_test.sql")
            .addAsWebInfResource(new StringAsset(datasourceXml), "test-ds.xml")
            .addAsResource(new StringAsset(flywayProperties), "META-INF/flyway.properties")
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }
    
    @Test
    public void testEnvironmentVariableConfiguration() throws Exception {
        // Give Flyway time to run migrations
        Thread.sleep(2000);
        
        // Get the datasource and verify migrations ran
        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/EnvVarTestDS");
        
        try (Connection conn = ds.getConnection()) {
            // Check that migration ran successfully
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Verify the test table was created
            List<String> tables = getTableNames(metaData);
            assertTrue(tables.contains("ENV_VAR_TEST"),
                     "Migration should have created env_var_test table");
            
            // Verify data was inserted
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT config_type FROM env_var_test WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("environment-variable", rs.getString(1),
                           "Configuration type should be environment-variable");
            }
            
        } catch (Exception e) {
            fail("Failed to verify migration execution: " + e.getMessage());
        }
    }
    
    @Test
    public void testEnvironmentVariableDefaults() throws Exception {
        // Verify that environment variable expressions with defaults resolved correctly.
        // Since no env vars are set, defaults should be used:
        // - ${env.FLYWAY_ENABLED:true} -> true  (Flyway ran)
        // - ${env.FLYWAY_DATASOURCE:java:jboss/datasources/EnvVarTestDS} -> EnvVarTestDS
        // - ${env.FLYWAY_BASELINE_ON_MIGRATE:false} -> false
        // - ${env.FLYWAY_LOCATIONS:classpath:db/migration} -> classpath:db/migration

        Thread.sleep(2000);

        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/EnvVarTestDS");

        try (Connection conn = ds.getConnection()) {
            // The schema history table must exist (proves defaults resolved correctly)
            DatabaseMetaData metaData = conn.getMetaData();
            List<String> tables = getTableNames(metaData);
            boolean hasSchemaHistory = tables.contains("FLYWAY_SCHEMA_HISTORY") ||
                                     tables.contains("flyway_schema_history");
            assertTrue(hasSchemaHistory,
                    "Flyway schema history table should exist when defaults resolve correctly");

            // Verify default locations worked (db/migration was found)
            // Use the actual table name present in the database
            String schemaTable = tables.contains("FLYWAY_SCHEMA_HISTORY")
                    ? "FLYWAY_SCHEMA_HISTORY" : "flyway_schema_history";
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM \"" + schemaTable + "\" WHERE \"type\" = 'SQL'")) {
                assertTrue(rs.next(), "Schema history should have entries");
                assertTrue(rs.getInt(1) > 0,
                        "SQL migration should be recorded, proving default locations resolved");
            }
        } catch (Exception e) {
            fail("Failed to verify environment variable defaults: " + e.getMessage());
        }
    }

    @Test
    public void testMigrationUsedCorrectDatasource() throws Exception {
        // Verify the cloud-native pattern works end-to-end:
        // The deployment used ${env.FLYWAY_DATASOURCE:java:jboss/datasources/EnvVarTestDS}
        // which should have resolved to EnvVarTestDS and the migration data should be there.

        Thread.sleep(2000);

        InitialContext ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/EnvVarTestDS");

        try (Connection conn = ds.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT config_type FROM env_var_test WHERE id = 1")) {
            assertTrue(rs.next(), "env_var_test should contain data");
            assertEquals("environment-variable", rs.getString(1),
                    "Data should match, proving the correct datasource was used via expression resolution");
        } catch (Exception e) {
            fail("Failed to verify datasource resolution: " + e.getMessage());
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