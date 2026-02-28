package com.github.wildfly.flyway.config;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds subsystem configuration read during the boot phase.
 *
 * The SubsystemAdd handler populates this holder with resolved attribute values
 * during server startup. The FlywayConfigurationBuilder reads from it during
 * deployment processing.
 *
 * This replaces the previous approach of reading the management model via
 * ModelControllerClient, which was removed in WildFly 35.
 */
public final class SubsystemConfigurationHolder {

    private static final AtomicReference<Properties> CONFIGURATION = new AtomicReference<>(new Properties());

    private SubsystemConfigurationHolder() {
        // Utility class
    }

    /**
     * Store the subsystem configuration. Called from SubsystemAdd during boot.
     *
     * @param properties resolved subsystem attribute values
     */
    public static void setConfiguration(Properties properties) {
        CONFIGURATION.set(properties != null ? properties : new Properties());
    }

    /**
     * Retrieve the subsystem configuration. Called from FlywayConfigurationBuilder
     * during deployment processing.
     *
     * @return a copy of the current subsystem configuration
     */
    public static Properties getConfiguration() {
        Properties copy = new Properties();
        copy.putAll(CONFIGURATION.get());
        return copy;
    }

    /**
     * Clear the configuration. Called from SubsystemRemove during shutdown.
     */
    public static void clear() {
        CONFIGURATION.set(new Properties());
    }
}
