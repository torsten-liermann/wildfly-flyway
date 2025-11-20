package com.github.wildfly.flyway.test.deployment;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test vendor-specific migration locations with {vendor} placeholder.
 */
@ExtendWith(ArquillianExtension.class)
public class FlywayVendorLocationTest {

    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
                        "    <datasource jndi-name=\"java:jboss/datasources/FlywayVendorTestDS\"\n" +
                        "                pool-name=\"FlywayVendorTestDS\"\n" +
                        "                enabled=\"true\"\n" +
                        "                use-java-context=\"true\">\n" +
                        "        <connection-url>jdbc:h2:mem:flyway-vendor-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
                        "        <driver>h2</driver>\n" +
                        "        <security>\n" +
                        "            <user-name>sa</user-name>\n" +
                        "            <password>sa</password>\n" +
                        "        </security>\n" +
                        "    </datasource>\n" +
                        "</datasources>";

        // Test with {vendor} placeholder
        String flywayProperties = 
            "spring.flyway.enabled=true\n" +
            "spring.flyway.datasource=java:jboss/datasources/FlywayVendorTestDS\n" +
            "spring.flyway.locations=classpath:db/vendors/{vendor},classpath:db/migration\n";

        // Create migration SQL inline
        String genericMigration = "CREATE TABLE PERSON (\n" +
                "    id INT PRIMARY KEY,\n" +
                "    first_name VARCHAR(255),\n" +
                "    last_name VARCHAR(255)\n" +
                ");";
        
        String h2Migration = "-- H2-specific migration\n" +
                "CREATE TABLE H2_SPECIFIC_TABLE (\n" +
                "    id INT PRIMARY KEY,\n" +
                "    database_type VARCHAR(50)\n" +
                ");\n" +
                "\n" +
                "INSERT INTO H2_SPECIFIC_TABLE (id, database_type) VALUES (1, 'H2');";
        
        String postgresqlMigration = "-- PostgreSQL-specific migration (should not be executed in H2 tests)\n" +
                "CREATE TABLE POSTGRESQL_SPECIFIC_TABLE (\n" +
                "    id SERIAL PRIMARY KEY,\n" +
                "    database_type VARCHAR(50)\n" +
                ");\n" +
                "\n" +
                "INSERT INTO POSTGRESQL_SPECIFIC_TABLE (database_type) VALUES ('PostgreSQL');";

        return ShrinkWrap.create(WebArchive.class, "flyway-vendor-test.war")
                // Generic migration
                .addAsResource(new StringAsset(genericMigration), "db/migration/V1__Init_person_table.sql")
                // H2-specific migration
                .addAsResource(new StringAsset(h2Migration), "db/vendors/h2/V2__H2_specific.sql")
                // PostgreSQL-specific migration (should be ignored)
                .addAsResource(new StringAsset(postgresqlMigration), "db/vendors/postgresql/V2__PostgreSQL_specific.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "flyway-vendor-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties");
    }

    @Test
    public void testVendorSpecificLocations() throws Exception {
        // Check that the generic migration was executed
        DataSource ds = (DataSource) new InitialContext().lookup("java:jboss/datasources/FlywayVendorTestDS");
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM PERSON");
             ResultSet rs = ps.executeQuery()) {
            
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1)); // Table should exist but be empty
        }
        
        // Check that the H2-specific migration was executed
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM H2_SPECIFIC_TABLE");
             ResultSet rs = ps.executeQuery()) {
            
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1)); // H2 specific table should have one row
        }
        
        // Check that PostgreSQL-specific migration was NOT executed
        // H2 uses INFORMATION_SCHEMA.TABLES with TABLE_NAME column in uppercase
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'POSTGRESQL_SPECIFIC_TABLE'");
             ResultSet rs = ps.executeQuery()) {
            
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1)); // PostgreSQL specific table should NOT exist
        }
    }
}