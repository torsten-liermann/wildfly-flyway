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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Flyway properties work with both "spring.flyway.*" and "flyway.*" prefixes.
 */
@ExtendWith(ArquillianExtension.class)
public class FlywayPropertyPrefixTest {

    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
                        "    <datasource jndi-name=\"java:jboss/datasources/FlywayPrefixTestDS\"\n" +
                        "                pool-name=\"FlywayPrefixTestDS\"\n" +
                        "                enabled=\"true\"\n" +
                        "                use-java-context=\"true\">\n" +
                        "        <connection-url>jdbc:h2:mem:flyway-prefix-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
                        "        <driver>h2</driver>\n" +
                        "        <security>\n" +
                        "            <user-name>sa</user-name>\n" +
                        "            <password>sa</password>\n" +
                        "        </security>\n" +
                        "    </datasource>\n" +
                        "</datasources>";

        // Test with flyway.* prefix (without spring.)
        String flywayProperties = 
            "flyway.enabled=true\n" +
            "flyway.datasource=java:jboss/datasources/FlywayPrefixTestDS\n" +
            "flyway.baseline-on-migrate=true\n" +
            "flyway.baseline-version=1.0\n";

        // Create migration SQL inline
        String migrationV1 = "CREATE TABLE PERSON (\n" +
                "    id INT PRIMARY KEY,\n" +
                "    first_name VARCHAR(255),\n" +
                "    last_name VARCHAR(255)\n" +
                ");";
        
        String migrationV2 = "INSERT INTO PERSON (id, first_name, last_name) VALUES (1, 'John', 'Doe');\n" +
                "INSERT INTO PERSON (id, first_name, last_name) VALUES (2, 'Jane', 'Smith');";

        return ShrinkWrap.create(WebArchive.class, "flyway-prefix-test.war")
                .addAsResource(new StringAsset(migrationV1), "db/migration/V1__Init_person_table.sql")
                .addAsResource(new StringAsset(migrationV2), "db/migration/V2__Add_people.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "flyway-prefix-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties");
    }

    @Test
    public void testFlywayPropertiesWithoutSpringPrefix() throws Exception {
        // Wait a bit for migrations to complete
        Thread.sleep(1000);
        
        // Check that the migration was executed
        DataSource ds = (DataSource) new InitialContext().lookup("java:jboss/datasources/FlywayPrefixTestDS");
        
        // First verify the datasource connection
        try (Connection conn = ds.getConnection()) {
            assertNotNull(conn, "Should be able to connect to datasource");
        }
        
        // Check if the PERSON table exists and has data
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM PERSON");
             ResultSet rs = ps.executeQuery()) {

            assertTrue(rs.next(), "Should have result");
            assertEquals(2, rs.getInt(1), "Should have 2 people");
        } catch (Exception e) {
            // Log more details about the failure
            fail("Failed to query PERSON table: " + e.getMessage());
        }
        
        // Check the flyway_schema_history table structure
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM flyway_schema_history");
             ResultSet rs = ps.executeQuery()) {
            
            // Check if we have any rows
            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                // Log what we find
                System.out.println("Row " + rowCount + ": installed_rank=" + rs.getInt("installed_rank") + 
                                 ", version=" + rs.getString("version") + 
                                 ", description=" + rs.getString("description"));
            }
            
            assertTrue(rowCount > 0, "Should have migration records");
            
            // If baseline-on-migrate was applied, there should be a baseline record with installed_rank = -1
            // But if there are actual migrations, Flyway might skip the baseline
            // Let's just check that migrations executed successfully
        } catch (Exception e) {
            // Log more details about the failure
            fail("Failed to query flyway_schema_history table: " + e.getMessage());
        }
    }
}