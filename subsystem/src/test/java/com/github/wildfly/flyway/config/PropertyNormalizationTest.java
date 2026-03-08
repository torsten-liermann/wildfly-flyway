package com.github.wildfly.flyway.config;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the production property normalization logic in
 * {@link FlywayConfigurationBuilder#normalizeKey(String)}.
 */
public class PropertyNormalizationTest {

    @Test
    public void testNormalizeFlywayPrefixToSpring() {
        assertEquals("spring.flyway.enabled",
                FlywayConfigurationBuilder.normalizeKey("flyway.enabled"));
        assertEquals("spring.flyway.datasource",
                FlywayConfigurationBuilder.normalizeKey("flyway.datasource"));
        assertEquals("spring.flyway.locations",
                FlywayConfigurationBuilder.normalizeKey("flyway.locations"));
        assertEquals("spring.flyway.baseline-on-migrate",
                FlywayConfigurationBuilder.normalizeKey("flyway.baseline-on-migrate"));
    }

    @Test
    public void testSpringPrefixUnchanged() {
        assertEquals("spring.flyway.enabled",
                FlywayConfigurationBuilder.normalizeKey("spring.flyway.enabled"));
        assertEquals("spring.flyway.datasource",
                FlywayConfigurationBuilder.normalizeKey("spring.flyway.datasource"));
    }

    @Test
    public void testNullReturnsNull() {
        assertNull(FlywayConfigurationBuilder.normalizeKey(null));
    }

    @Test
    public void testUnknownPrefixReturnsNull() {
        assertNull("Keys outside both namespaces should return null",
                FlywayConfigurationBuilder.normalizeKey("other.property"));
    }
}
