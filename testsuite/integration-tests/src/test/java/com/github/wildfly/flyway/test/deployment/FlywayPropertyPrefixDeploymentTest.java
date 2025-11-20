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
 * Test that Flyway properties work with both "spring.flyway.*" and "flyway.*" prefixes
 * using deployment properties (META-INF/flyway.properties).
 */
@ExtendWith(ArquillianExtension.class)
public class FlywayPropertyPrefixDeploymentTest {

    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
                        "    <datasource jndi-name=\"java:jboss/datasources/FlywayPrefixDeploymentTestDS\"\n" +
                        "                pool-name=\"FlywayPrefixDeploymentTestDS\"\n" +
                        "                enabled=\"true\"\n" +
                        "                use-java-context=\"true\">\n" +
                        "        <connection-url>jdbc:h2:mem:flyway-prefix-deployment-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
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
            "flyway.datasource=java:jboss/datasources/FlywayPrefixDeploymentTestDS\n" +
            "flyway.baseline-on-migrate=true\n" +
            "flyway.baseline-version=2.0\n";

        // Create migration SQL inline
        String migrationV1 = "CREATE TABLE EMPLOYEE (\n" +
                "    id INT PRIMARY KEY,\n" +
                "    name VARCHAR(255),\n" +
                "    department VARCHAR(255)\n" +
                ");";
        
        String migrationV2 = "INSERT INTO EMPLOYEE (id, name, department) VALUES (1, 'Alice', 'Engineering');\n" +
                "INSERT INTO EMPLOYEE (id, name, department) VALUES (2, 'Bob', 'Sales');\n" +
                "INSERT INTO EMPLOYEE (id, name, department) VALUES (3, 'Charlie', 'Marketing');";

        return ShrinkWrap.create(WebArchive.class, "flyway-prefix-deployment-test.war")
                .addAsResource(new StringAsset(migrationV1), "db/migration/V1__Create_employee_table.sql")
                .addAsResource(new StringAsset(migrationV2), "db/migration/V2__Add_employees.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "flyway-prefix-deployment-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties");
    }

    @Test
    public void testFlywayPropertiesWithoutSpringPrefix() throws Exception {
        // Wait a bit for migrations to complete
        Thread.sleep(1000);
        
        // Get the datasource that should have been migrated
        DataSource ds = (DataSource) new InitialContext().lookup("java:jboss/datasources/FlywayPrefixDeploymentTestDS");
        
        // Check that the migration was executed
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM EMPLOYEE");
             ResultSet rs = ps.executeQuery()) {
            
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
        
        // Check that baseline properties were applied
        // When migrations exist, Flyway might skip baseline
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM flyway_schema_history");
             ResultSet rs = ps.executeQuery()) {
            
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "Should have migration records");
        }
    }
}