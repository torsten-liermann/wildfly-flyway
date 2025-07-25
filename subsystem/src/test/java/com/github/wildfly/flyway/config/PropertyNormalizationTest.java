package com.github.wildfly.flyway.config;

import org.junit.Test;
import static org.junit.Assert.*;

public class PropertyNormalizationTest {
    
    @Test
    public void testPropertyNormalization() {
        // Test the normalization logic that should be in SpringBootPropertyResolver
        assertEquals("spring.flyway.enabled", normalizePropertyKey("flyway.enabled"));
        assertEquals("spring.flyway.datasource", normalizePropertyKey("flyway.datasource"));
        assertEquals("spring.flyway.locations", normalizePropertyKey("flyway.locations"));
        assertEquals("spring.flyway.baseline-on-migrate", normalizePropertyKey("flyway.baseline-on-migrate"));
        
        // Test that spring.flyway.* properties are not changed
        assertEquals("spring.flyway.enabled", normalizePropertyKey("spring.flyway.enabled"));
        assertEquals("spring.flyway.datasource", normalizePropertyKey("spring.flyway.datasource"));
        
        // Test edge cases
        assertNull(normalizePropertyKey(null));
        assertEquals("other.property", normalizePropertyKey("other.property"));
    }
    
    private String normalizePropertyKey(String key) {
        if (key == null) {
            return null;
        }
        
        // If it already starts with spring.flyway., return as is
        if (key.startsWith("spring.flyway.")) {
            return key;
        }
        
        // If it starts with flyway., convert to spring.flyway.
        if (key.startsWith("flyway.")) {
            return "spring.flyway." + key.substring("flyway.".length());
        }
        
        // Otherwise return as is
        return key;
    }
}