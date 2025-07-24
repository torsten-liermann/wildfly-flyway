package com.github.wildfly.flyway.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Basic unit test for Flyway extension components.
 */
public class BasicFlywayTest {

    @Test
    public void testExtensionConstants() {
        assertEquals("flyway", FlywayExtension.SUBSYSTEM_NAME);
        assertNotNull(FlywayExtension.SUBSYSTEM_PATH);
    }

    @Test
    public void testNamespace() {
        assertEquals("urn:wildfly:flyway:1.0", FlywaySubsystemNamespace.FLYWAY_1_0.getUriString());
    }

    @Test
    public void testParserInstance() {
        assertNotNull(FlywaySubsystemParser.INSTANCE);
    }

    @Test
    public void testWriterInstance() {
        assertNotNull(FlywaySubsystemWriter.INSTANCE);
    }
}