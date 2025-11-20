package com.github.wildfly.flyway.test.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
public class SimpleFlywayTest {

    private static final String TEST_DS = "java:jboss/datasources/SimpleFlywayTestDS";
    
    @ArquillianResource
    private InitialContext context;

    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
            "    <datasource jndi-name=\"java:jboss/datasources/SimpleFlywayTestDS\"\n" +
            "                pool-name=\"SimpleFlywayTestDS\"\n" +
            "                enabled=\"true\"\n" +
            "                use-java-context=\"true\">\n" +
            "        <connection-url>jdbc:h2:mem:simple-flyway-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
            "        <driver>h2</driver>\n" +
            "        <security>\n" +
            "            <user-name>sa</user-name>\n" +
            "            <password>sa</password>\n" +
            "        </security>\n" +
            "    </datasource>\n" +
            "</datasources>";
            
        String flywayProperties = 
            "spring.flyway.enabled=true\n" +
            "spring.flyway.datasource=java:jboss/datasources/SimpleFlywayTestDS\n";
        
        return ShrinkWrap.create(WebArchive.class, "simple-flyway-test.war")
                .addAsResource("db/migration/V100__Simple_Test.sql", "db/migration/V100__Simple_Test.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "simple-flyway-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties")
                .addClass(SimpleFlywayTest.class);
    }

    @Test
    public void testSimpleDeployment() throws Exception {
        // Get datasource  
        DataSource dataSource = (DataSource) context.lookup(TEST_DS);
        
        // Check that the migration was executed
        boolean tableExists = false;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SIMPLE_TEST'")) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                tableExists = true;
            }
        }
        
        assertTrue(tableExists, "SIMPLE_TEST table should exist");
    }
}
