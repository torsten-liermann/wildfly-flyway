package com.github.wildfly.flyway.config;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests for the SQL injection and security patterns used in SpringBootPropertyResolver.
 * Ensures that legitimate values (JDBC URLs, Flyway locations) are not falsely rejected,
 * while actual injection patterns are still detected.
 */
public class SecurityPatternTest {

    // Must match the pattern in SpringBootPropertyResolver exactly
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "('.+--)|(--)|(\\*/)|(/\\*)|(char\\()|('\\s*or\\s*'1'\\s*=\\s*'1)|(\\s*or\\s*1\\s*=\\s*1)",
            Pattern.CASE_INSENSITIVE);

    @Test
    public void testLegitimateJdbcUrlsNotFlagged() {
        // Semicolons are legitimate in JDBC URLs
        assertFalse("SQL Server JDBC URL should not be flagged",
                SQL_INJECTION_PATTERN.matcher("jdbc:sqlserver://host;instance=MSSQL").find());
        assertFalse("H2 JDBC URL with semicolon params should not be flagged",
                SQL_INJECTION_PATTERN.matcher("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1").find());
        assertFalse("Oracle JDBC URL should not be flagged",
                SQL_INJECTION_PATTERN.matcher("jdbc:oracle:thin:@host:1521:orcl").find());
    }

    @Test
    public void testLegitimateFlywayLocationsNotFlagged() {
        // Semicolons can separate multiple locations
        assertFalse("Multiple locations should not be flagged",
                SQL_INJECTION_PATTERN.matcher("classpath:db/migration;classpath:db/specific").find());
    }

    @Test
    public void testLegitimatePropertiesNotFlagged() {
        // @ is legitimate in email addresses and property values
        assertFalse("Email-like value should not be flagged",
                SQL_INJECTION_PATTERN.matcher("admin@example.com").find());
        assertFalse("JNDI name should not be flagged",
                SQL_INJECTION_PATTERN.matcher("java:jboss/datasources/MyDS").find());
    }

    @Test
    public void testSqlInjectionPatternsStillDetected() {
        // SQL comment injection
        assertTrue("SQL comment should be detected",
                SQL_INJECTION_PATTERN.matcher("value' --").find());
        assertTrue("Block comment open should be detected",
                SQL_INJECTION_PATTERN.matcher("value /*").find());
        assertTrue("Block comment close should be detected",
                SQL_INJECTION_PATTERN.matcher("value */").find());

        // SQL injection via OR
        assertTrue("OR 1=1 injection should be detected",
                SQL_INJECTION_PATTERN.matcher("' or '1'='1").find());
        assertTrue("OR 1=1 numeric injection should be detected",
                SQL_INJECTION_PATTERN.matcher(" or 1=1").find());

        // CHAR() based injection
        assertTrue("CHAR() function should be detected",
                SQL_INJECTION_PATTERN.matcher("char(65)").find());

        // Double dash
        assertTrue("Double dash should be detected",
                SQL_INJECTION_PATTERN.matcher("-- comment").find());
    }
}
