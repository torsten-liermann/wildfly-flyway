package com.github.wildfly.flyway.config;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds subsystem configuration read during the boot phase.
 *
 * <p>The SubsystemAdd handler populates this holder with resolved attribute values
 * during server startup. The FlywayConfigurationBuilder reads from it during
 * deployment processing.</p>
 *
 * <p>This replaces the previous approach of reading the management model via
 * ModelControllerClient, which was removed in WildFly 35.</p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>Populated in {@code FlywaySubsystemAdd.performBoottime(...)} on server boot.</li>
 *   <li>Re-populated on every reload that re-runs the boot phase. Runtime
 *       attribute writes are flagged {@code reload-required}; they only become
 *       visible to deployments after a reload re-invokes {@code performBoottime}.</li>
 *   <li>Subsystem-Remove deliberately does <strong>not</strong> clear the holder:
 *       deployment services installed under the previous subsystem may still
 *       observe it, and the next subsystem-add overwrites it atomically.</li>
 * </ul>
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

}
