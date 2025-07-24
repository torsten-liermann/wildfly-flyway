package com.github.wildfly.flyway.test.deployment;

import com.github.wildfly.flyway.arquillian.ChangeLogDefinition;
import com.github.wildfly.flyway.arquillian.FlywayTestSupport;
import com.github.wildfly.flyway.arquillian.ResourceLocation;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class FlywayWarRootDeploymentTest extends FlywayTestSupport {

    @ChangeLogDefinition(resourceLocation = ResourceLocation.ROOT)
    private String tableNameRoot;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(WebArchive.class, "flyway-war-root-deployment-test.war");
    }

    @Test
    public void testWarDeploymentRootResource() throws Exception {
        assertTableModified(tableNameRoot);
    }
}