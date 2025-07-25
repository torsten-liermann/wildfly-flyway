# WildFly Flyway Subsystem - Test Notes

## SEVERE Log Messages in Tests

The following SEVERE log messages appear during test execution and are **expected behavior**:

```
SEVERE [org.flywaydb.core.internal.command.DbMigrate] Schema "PUBLIC" has version 2, but no migration could be resolved in the configured locations !
```

### Why This Happens

1. All integration tests share the same H2 in-memory database (ExampleDS with URL `jdbc:h2:mem:test`)
2. The FlywayMigrationTest runs first and executes migrations V1 and V2
3. Subsequent tests (SimpleDeploymentTest, FlywaySubsystemTest) deploy without migration files
4. Flyway correctly detects that the database is already at version 2 but finds no migrations to apply

### This is Working as Designed

- The SEVERE log level comes from Flyway itself, not our subsystem
- The message indicates Flyway is working correctly - it's validating the schema version
- Tests without migrations still trigger Flyway (showing automatic migration execution works)
- The shared database state between tests is a limitation of the test environment

### Test Configuration

The test configuration in `flyway-test.properties` includes:

```properties
spring.flyway.enabled=true
spring.flyway.validate-on-migrate=false
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=99
spring.flyway.clean-disabled=false
spring.flyway.clean-on-validation-error=true
```

This configuration helps reduce (but not eliminate) the SEVERE logs by:
- Disabling validation on migrate
- Enabling baseline on migrate
- Allowing clean operations for test scenarios

### Test Isolation Solution - Test-Specific DataSources

The test isolation issue has been successfully resolved by implementing test-specific DataSources:

1. **Each test deploys its own DataSource** via `-ds.xml` in the deployment
2. **Each DataSource uses a unique H2 database** with different memory database names
3. **FlywayDeploymentProcessor reads datasource from properties** in `META-INF/flyway-test.properties`

Example implementation:
```java
String datasourceXml = 
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
    "<datasources xmlns=\"urn:jboss:domain:datasources:7.0\">\n" +
    "    <datasource jndi-name=\"java:jboss/datasources/FlywayMigrationTestDS\"\n" +
    "                pool-name=\"FlywayMigrationTestDS\"\n" +
    "                enabled=\"true\"\n" +
    "                use-java-context=\"true\">\n" +
    "        <connection-url>jdbc:h2:mem:flyway-migration-test;DB_CLOSE_DELAY=-1</connection-url>\n" +
    "        <driver>h2</driver>\n" +
    "        <security>\n" +
    "            <user-name>sa</user-name>\n" +
    "            <password>sa</password>\n" +
    "        </security>\n" +
    "    </datasource>\n" +
    "</datasources>";

String flywayProperties = 
    "spring.flyway.enabled=true\n" +
    "spring.flyway.datasource=java:jboss/datasources/FlywayMigrationTestDS\n";

// Deploy with test-specific datasource
.addAsWebInfResource(new StringAsset(datasourceXml), "flyway-migration-test-ds.xml")
.addAsManifestResource(new StringAsset(flywayProperties), "flyway-test.properties")
```

This approach:
- **No SEVERE logs** - Each test has its own isolated database
- **No validation conflicts** - Tests don't share migration history
- **Parallel test execution** - Tests can run concurrently without interference
- **Simple implementation** - Uses standard WildFly datasource deployment

The only drawback is the deprecation warning for `-ds.xml` deployments, but this is acceptable for test scenarios.