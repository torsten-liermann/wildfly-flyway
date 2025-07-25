# WildFly Flyway Subsystem - Developer Guide

[![CI](https://github.com/torsten-liermann/wildfly-flyway/actions/workflows/ci.yml/badge.svg)](https://github.com/torsten-liermann/wildfly-flyway/actions/workflows/ci.yml)

A WildFly subsystem that integrates Flyway database migrations into the application server's deployment lifecycle.

## Table of Contents

- [Architecture](#architecture)
- [Setting up the Development Environment](#setting-up-the-development-environment)
- [Project Structure](#project-structure)
- [Configuration Mechanism](#configuration-mechanism)
- [Development Guide](#development-guide)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## Architecture

### Key Design Principles

- **Zero Configuration**: Automatic migration discovery in standard locations
- **Spring Boot Compatibility**: Uses `spring.flyway.*` property naming conventions
- **Deployment Isolation**: Each deployment has its own migration configuration
- **Production Ready**: Clean operations disabled by default, comprehensive input validation
- **WildFly Native**: Full integration with management model and lifecycle

### Core Components

```
wildfly-flyway/
├── subsystem/              # Core subsystem implementation
│   ├── extension/          # FlywayExtension - subsystem registration
│   ├── deployment/         # FlywayDeploymentProcessor - deployment discovery
│   ├── service/           # FlywayMigrationService - migration execution
│   ├── config/            # SpringBootPropertyResolver - property handling
│   └── security/          # SecurityInputValidator - input validation
├── feature-pack/          # WildFly feature pack for distribution
└── itests/                # Arquillian integration tests
```

### Key Classes and Their Responsibilities

- **FlywayExtension**: Registers the subsystem with WildFly
- **FlywaySubsystemDefinition**: Defines management model attributes
- **FlywayDeploymentProcessor**: Discovers deployments with migrations, creates migration services
- **FlywayMigrationService**: MSC service that executes Flyway migrations
- **SpringBootPropertyResolver**: Maps Spring Boot properties to Flyway configuration

## Setting up the Development Environment

The project uses Arquillian integration tests which require a provisioned WildFly server. The build process handles this automatically.

1. **Build the project:**
   ```bash
   mvn clean install
   ```
   This command compiles all modules, runs the tests, and provisions a WildFly server instance in `itests/target/wildfly-28.0.1.Final`.

2. **Run specific tests:** To iterate quickly, you can run a single test against the already provisioned server:
   ```bash
   mvn test -pl itests -Dtest=SimpleFlywayTest
   ```

3. **Debug tests:**
   ```bash
   mvn test -pl itests -Dmaven.surefire.debug -Dtest=SimpleFlywayTest
   ```
   Then connect your debugger to port 5005.

## Project Structure

### Module Dependencies

- **subsystem**: Core implementation, depends on WildFly controller APIs and Flyway
- **feature-pack**: Galleon feature pack definition for distribution
- **itests**: Integration tests using Arquillian

### Key Files

```
subsystem/src/main/
├── java/.../
│   ├── extension/FlywayExtension.java          # Entry point
│   ├── deployment/FlywayDeploymentProcessor.java # Deployment handling
│   └── config/SpringBootPropertyResolver.java   # Property resolution
└── resources/
    ├── LocalDescriptions.properties             # Management descriptions
    └── subsystem-templates/flyway.xml          # Default configuration
```

## Configuration Mechanism

The subsystem is primarily configured via a `flyway.properties` file within the deployment. It emulates Spring Boot's property resolution.

- **Property Source:** Properties are read from `META-INF/flyway.properties` in the deployment's classpath. System properties can override.
- **Resolution Logic:** The core logic resides in `SpringBootPropertyResolver.java`. This class handles default values, expression resolution (`${...}`), and vendor-specific paths (`{vendor}`).
- **Activation:** Migration is activated for a deployment if a datasource is configured (e.g., via `spring.flyway.datasource`) AND either migration scripts are found in a default location (like `db/migration`) or `spring.flyway.enabled` is explicitly set to true.

### Configuration Priority

1. `META-INF/flyway.properties` in deployment
2. System properties
3. Default datasource (`java:jboss/datasources/ExampleDS`)

## Development Guide

This guide covers key extension points of the subsystem.

### Adding a New Configuration Property

To add support for a new Flyway property (e.g., `spring.flyway.new-property`):

1. **Register the Property:** Add the new property to the `PROPERTY_MAPPINGS` map in `SpringBootPropertyResolver.java`:
   ```java
   PROPERTY_MAPPINGS.put("spring.flyway.new-property", "newProperty");
   ```

2. **Apply the Configuration:** In `FlywayMigrationService.java`, within the `applyConfiguration` method, add logic to apply the resolved property:
   ```java
   // In applyConfiguration()
   String newValue = resolvedProperties.get("newProperty");
   if (newValue != null) {
       configuration.newFlywayMethod(newValue);
   }
   ```

3. **Add a Test:** Create a new integration test in the `itests` module to verify that the new property is correctly applied.

### Modifying the Management Model

The subsystem's management model is defined in `FlywaySubsystemDefinition.java`.

- To add a new attribute, edit the `AttributeDefinition` list
- XML parsing logic is in `FlywaySubsystemParser.java`
- Test changes with `FlywaySubsystemTestCase.java`

### Understanding the Deployment Process

1. **Discovery**: `FlywayDeploymentProcessor.hasMigrations()` checks for migration files
2. **Service Creation**: `createMigrationService()` creates an MSC service for the deployment
3. **Execution**: `FlywayMigrationService.start()` executes migrations during deployment
4. **Cleanup**: Service stops when deployment is undeployed

### Security Considerations

All user input must be validated using `SecurityInputValidator`:

```java
String validated = SecurityInputValidator.validateDataSourceJndiName(input);
```

Key security features:
- Clean operations disabled by default
- JNDI name validation
- SQL script path validation
- Input sanitization for all properties

## Testing

### Test Structure

```
itests/src/test/
├── java/.../test/
│   ├── deployment/           # Deployment-based tests
│   │   ├── SimpleFlywayTest.java
│   │   ├── FlywayMigrationTest.java
│   │   └── FlywayMigrationErrorTest.java
│   └── subsystem/           # Management model tests
│       └── FlywaySubsystemTest.java
└── resources/
    └── db/migration/        # Test migration scripts
```

### Writing Integration Tests

```java
@RunWith(Arquillian.class)
public class MyFlywayTest {
    @Deployment
    public static Archive<?> deployment() {
        String datasourceXml = """
            <datasources xmlns="urn:jboss:domain:datasources:7.0">
                <datasource jndi-name="java:jboss/datasources/TestDS">
                    <!-- datasource configuration -->
                </datasource>
            </datasources>
            """;
            
        String flywayProperties = """
            spring.flyway.enabled=true
            spring.flyway.datasource=java:jboss/datasources/TestDS
            """;
        
        return ShrinkWrap.create(WebArchive.class, "test.war")
            .addAsResource("db/migration/V1__Init.sql")
            .addAsWebInfResource(new StringAsset(datasourceXml), "test-ds.xml")
            .addAsResource(new StringAsset(flywayProperties), "META-INF/flyway.properties");
    }
    
    @Test
    public void testMigration() {
        // Verify migration execution
    }
}
```

### Running Specific Test Suites

```bash
# Unit tests only
mvn test -pl subsystem

# Integration tests only
mvn test -pl itests

# All tests
mvn clean verify
```

## Troubleshooting

### Common Development Issues

1. **Test failures due to port conflicts**
   - The test server uses ports 18080 (HTTP) and 19990 (management)
   - Check `arquillian.xml` for port configuration

2. **ClassLoader issues in tests**
   - Ensure test resources are in the correct location
   - Use deployment classloader for resource loading

3. **Service dependency failures**
   - Check datasource JNDI names match exactly
   - Verify service dependencies in `FlywayDeploymentProcessor`

### Debugging Tips

1. **Enable debug logging:**
   ```xml
   <logger category="com.github.wildfly.flyway">
       <level name="DEBUG"/>
   </logger>
   ```

2. **Check service status:**
   ```
   /subsystem=service-container:dump-services()
   ```

3. **Arquillian debugging:**
   - Server logs: `itests/target/wildfly-28.0.1.Final/standalone/log/server.log`
   - Set `allowConnectingToRunningServer=false` in `arquillian.xml` for isolated tests

## Contributing

### Code Style

- Standard Java conventions
- Meaningful variable names
- Javadoc for public APIs
- Keep methods focused and testable

### Pull Request Process

1. Fork and create a feature branch
2. Write tests for new functionality
3. Ensure all tests pass: `mvn clean verify`
4. Update documentation if needed
5. Submit PR with clear description

### Commit Message Format

```
feat: Add support for custom migration loaders
fix: Resolve datasource lookup timing issue
test: Add test for multi-schema migrations
docs: Update developer guide
```

## License

Apache License 2.0