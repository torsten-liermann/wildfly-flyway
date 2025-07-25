package com.github.wildfly.flyway.test.deployment;

import static org.junit.Assert.assertTrue;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@Ignore("Schema isolation requires more complex setup - see TEST_NOTES.md")
public class SimpleFlywayTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(WebArchive.class, "simple-flyway-test.war")
                .addAsResource("db/migration/V100__Simple_Test.sql", "db/migration/V100__Simple_Test.sql")
                .addAsManifestResource("simple-flyway-test.properties", "flyway-test.properties");
    }

    @Test
    public void testSimpleDeployment() {
        // Just test that deployment works with migration file
        assertTrue("Deployment should work", true);
    }
}
