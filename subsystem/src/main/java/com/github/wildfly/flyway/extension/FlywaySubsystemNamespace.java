package com.github.wildfly.flyway.extension;

/**
 * Flyway subsystem XML namespace.
 */
public enum FlywaySubsystemNamespace {
    
    FLYWAY_1_0("urn:wildfly:flyway:1.0");
    
    private final String uriString;
    
    FlywaySubsystemNamespace(String uriString) {
        this.uriString = uriString;
    }
    
    public String getUriString() {
        return uriString;
    }
}