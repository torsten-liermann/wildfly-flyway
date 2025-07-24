package com.github.wildfly.flyway.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Basic unit test for Flyway extension components.
 */
class BasicFlywayTest {

    @Test
    void testExtensionConstants() {
        assertEquals("flyway", FlywayExtension.SUBSYSTEM_NAME);
        assertNotNull(FlywayExtension.SUBSYSTEM_PATH);
    }

    @Test
    void testNamespace() {
        assertEquals("urn:wildfly:flyway:1.0", FlywaySubsystemNamespace.FLYWAY_1_0.getUriString());
    }

    @Test
    void testParserInstance() {
        assertNotNull(FlywaySubsystemParser.INSTANCE);
    }

    @Test
    void testWriterInstance() {
        assertNotNull(FlywaySubsystemWriter.INSTANCE);
    }
}