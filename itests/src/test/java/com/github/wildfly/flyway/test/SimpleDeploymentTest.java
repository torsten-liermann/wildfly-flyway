package com.github.wildfly.flyway.test;

import static org.junit.Assert.assertTrue;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class SimpleDeploymentTest {

    @Deployment
    public static Archive<?> deployment() {
        // Test deployment without any migration files
        // This tests that Flyway doesn't fail when no migrations exist
        return ShrinkWrap.create(WebArchive.class, "simple-test.war");
    }

    @Test
    public void testSimpleDeployment() {
        // Just test that deployment works
        assertTrue("Deployment should work", true);
    }
}
