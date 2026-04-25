package com.github.wildfly.flyway.extension;

import com.github.wildfly.flyway.config.FlywayConfiguration;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Guards against drift between the two surviving sources of default values:
 *
 * <ol>
 *   <li>{@code FlywaySubsystemDefinition.*} {@link AttributeDefinition} defaults exposed via the management
 *       schema and applied during XML parsing.</li>
 *   <li>{@code FlywayConfiguration.DEFAULTS} applied to the Flyway FluentConfiguration when a property
 *       is otherwise unset, exposed via {@link FlywayConfiguration#getDefault(String)}.</li>
 * </ol>
 *
 * Both layers must agree on the effective value so that an empty
 * {@code <subsystem xmlns="..."/>} produces the same migration behaviour as a deployment with
 * no overrides at all.
 */
public class DefaultsConsistencyTest {

    @Test
    public void enabledDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.ENABLED, FlywayConfiguration.ENABLED);
    }

    @Test
    public void baselineOnMigrateDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.BASELINE_ON_MIGRATE,
                FlywayConfiguration.BASELINE_ON_MIGRATE);
    }

    @Test
    public void cleanDisabledDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.CLEAN_DISABLED,
                FlywayConfiguration.CLEAN_DISABLED);
    }

    @Test
    public void validateOnMigrateDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.VALIDATE_ON_MIGRATE,
                FlywayConfiguration.VALIDATE_ON_MIGRATE);
    }

    @Test
    public void locationsDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.LOCATIONS, FlywayConfiguration.LOCATIONS);
    }

    @Test
    public void tableDefaultMatchesFlywayDefault() {
        assertSubsystemAndCodeDefaultMatch(FlywaySubsystemDefinition.TABLE, FlywayConfiguration.TABLE);
    }

    private static void assertSubsystemAndCodeDefaultMatch(AttributeDefinition subsystemAttr,
                                                           String flywayConfigKey) {
        ModelNode subsystemDefault = subsystemAttr.getDefaultValue();
        if (subsystemDefault == null || !subsystemDefault.isDefined()) {
            fail("Subsystem attribute " + subsystemAttr.getName() + " has no default value to compare");
        }
        String configDefault = FlywayConfiguration.getDefault(flywayConfigKey);
        assertFalse("FlywayConfiguration.DEFAULTS missing key " + flywayConfigKey,
                configDefault == null || configDefault.isEmpty());
        assertEquals("Default drift between subsystem attribute " + subsystemAttr.getName()
                        + " and FlywayConfiguration." + flywayConfigKey,
                subsystemDefault.asString(), configDefault);
    }
}
