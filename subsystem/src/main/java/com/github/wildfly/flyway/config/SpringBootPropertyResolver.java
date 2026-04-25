package com.github.wildfly.flyway.config;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects the database vendor from a JDBC URL for Flyway location placeholder
 * resolution ({@code {vendor}}). Used by {@link FlywayConfigurationBuilder}.
 */
public final class SpringBootPropertyResolver {

    private static final Logger LOGGER = Logger.getLogger(SpringBootPropertyResolver.class.getName());

    private SpringBootPropertyResolver() {
    }

    public static String detectVendor(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return null;
        }

        String url = jdbcUrl.toLowerCase();

        if (url.contains(":h2:")) {
            return "h2";
        } else if (url.contains(":postgresql:")) {
            return "postgresql";
        } else if (url.contains(":mysql:")) {
            return "mysql";
        } else if (url.contains(":mariadb:")) {
            return "mariadb";
        } else if (url.contains(":oracle:")) {
            return "oracle";
        } else if (url.contains(":sqlserver:") || url.contains(":jtds:")) {
            return "sqlserver";
        } else if (url.contains(":db2:")) {
            return "db2";
        }

        LOGGER.log(Level.FINE, "Unknown database vendor for URL: {0}", jdbcUrl);
        return null;
    }
}
