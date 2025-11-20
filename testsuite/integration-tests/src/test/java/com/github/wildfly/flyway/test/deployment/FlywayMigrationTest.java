package com.github.wildfly.flyway.test.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ArquillianExtension.class)
public class FlywayMigrationTest {

    private static final String TEST_DS = "java:jboss/datasources/FlywayMigrationTestDS";
    private static final String QUERY_TABLES = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'";
    private static final String QUERY_TABLE_COLUMNS = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY COLUMN_NAME ASC";

    @ArquillianResource
    private InitialContext context;

    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
            "    <datasource jndi-name=\"java:jboss/datasources/FlywayMigrationTestDS\"\n" +
            "                pool-name=\"FlywayMigrationTestDS\"\n" +
            "                enabled=\"true\"\n" +
            "                use-java-context=\"true\">\n" +
            "        <connection-url>jdbc:h2:mem:flyway-migration-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
            "        <driver>h2</driver>\n" +
            "        <security>\n" +
            "            <user-name>sa</user-name>\n" +
            "            <password>sa</password>\n" +
            "        </security>\n" +
            "    </datasource>\n" +
            "</datasources>";
            
        String flywayProperties = 
            "spring.flyway.enabled=true\n" +
            "spring.flyway.datasource=java:jboss/datasources/FlywayMigrationTestDS\n";
        
        return ShrinkWrap.create(WebArchive.class, "flyway-migration-test.war")
                .addAsResource("db/migration/V1__Create_person_table.sql", "db/migration/V1__Create_person_table.sql")
                .addAsResource("db/migration/V2__Add_people.sql", "db/migration/V2__Add_people.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "flyway-migration-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties")
                .addClass(FlywayMigrationTest.class);
    }

    @Test
    public void testFlywayMigrationExecuted() throws Exception {
        // Get datasource
        DataSource dataSource = (DataSource) context.lookup(TEST_DS);
        
        // Check that the migrations were executed
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(QUERY_TABLES)) {
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }
        }
        
        // Should have flyway_schema_history and PERSON tables
        assertTrue(tables.contains("flyway_schema_history"), "Should have flyway_schema_history table");
        assertTrue(tables.contains("PERSON"), "Should have PERSON table");
        
        // Check columns in PERSON table
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(QUERY_TABLE_COLUMNS)) {
                statement.setString(1, "PERSON");
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        
        // Sort columns for comparison since order doesn't matter
        columns.sort(String::compareTo);
        List<String> expectedColumns = Arrays.asList("id", "first_name", "last_name");
        expectedColumns.sort(String::compareTo);
        assertEquals(expectedColumns, columns, "PERSON table should have expected columns");

        // Check data was inserted
        int count = 0;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM PERSON")) {
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    count = resultSet.getInt(1);
                }
            }
        }

        assertEquals(3, count, "Should have 3 people in PERSON table");
    }
}
