# WildFly Flyway Subsystem

The WildFly Flyway subsystem provides Flyway integration for WildFly Application Server.

## Features

* Integrates Flyway into the WildFly subsystem model
* Spring Boot style configuration properties support
* Automatic migration on deployment
* Support for multiple datasources
* Management operations for migration control

## Configuration

### Flyway Configuration via Deployment Descriptor

The subsystem supports configuring Flyway migrations using a deployment descriptor approach. Create a `META-INF/flyway.properties` file in your deployment (WAR/EAR) with Spring Boot style properties:

```properties
# Enable Flyway migrations
spring.flyway.enabled=true

# Specify the datasource JNDI name
spring.flyway.datasource=java:jboss/datasources/MyDS

# Additional Flyway configuration
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.schemas=myschema
```

### Property Resolution

Properties in `flyway.properties` support WildFly expression resolution:

```properties
spring.flyway.datasource=${db.datasource:java:jboss/datasources/ExampleDS}
spring.flyway.schemas=${db.schema:public}
```

### Configuration Priority

The subsystem looks for configuration in the following order:

1. `META-INF/flyway.properties` in the deployment
2. System properties
3. Default datasource (`java:jboss/datasources/ExampleDS`)

## Building

```bash
mvn clean install
```

## Installation

See the documentation for installation instructions.

## License

Apache License 2.0