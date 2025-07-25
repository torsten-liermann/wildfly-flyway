package com.github.wildfly.flyway.test.deployment;

import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.ShouldThrowException;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class FlywayMigrationErrorTest {

    @Deployment
    @ShouldThrowException(DeploymentException.class)
    public static Archive<?> deployment() {
        String datasourceXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
                        "    <datasource jndi-name=\"java:jboss/datasources/FlywayErrorTestDS\"\n" +
                        "                pool-name=\"FlywayErrorTestDS\"\n" +
                        "                enabled=\"true\"\n" +
                        "                use-java-context=\"true\">\n" +
                        "        <connection-url>jdbc:h2:mem:flyway-error-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE</connection-url>\n" +
                        "        <driver>h2</driver>\n" +
                        "        <security>\n" +
                        "            <user-name>sa</user-name>\n" +
                        "            <password>sa</password>\n" +
                        "        </security>\n" +
                        "    </datasource>\n" +
                        "</datasources>";

        String flywayProperties = 
            "spring.flyway.enabled=true\n" +
            "spring.flyway.datasource=java:jboss/datasources/FlywayErrorTestDS\n";
        
        return ShrinkWrap.create(WebArchive.class, "flyway-error-test.war")
                .addAsResource("db/migration/V1__Faulty_migration.sql", "db/migration/V1__Faulty_migration.sql")
                .addAsWebInfResource(new StringAsset(datasourceXml), "flyway-error-test-ds.xml")
                .addAsManifestResource(new StringAsset(flywayProperties), "flyway.properties");
    }

    @Test
    public void testDeploymentShouldFail() {
        // If this method is called, it means the deployment succeeded when it should have failed.
    }
}
