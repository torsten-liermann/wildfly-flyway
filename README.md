# WildFly Flyway Subsystem

[![CI](https://github.com/torsten-liermann/wildfly-flyway/actions/workflows/ci.yml/badge.svg)](https://github.com/torsten-liermann/wildfly-flyway/actions/workflows/ci.yml)

Automatic database migration for Java EE applications deployed to WildFly.

## Overview

The WildFly Flyway subsystem integrates [Flyway](https://flywaydb.org/) database migrations into your application server. When you deploy an application with SQL migration scripts, they are automatically executed against your configured datasource.

**Key Features:**
- Zero application code required - just add SQL scripts
- Automatic migration on deployment
- Spring Boot compatible configuration
- Full WildFly expression resolution for environment variables and system properties
- Three-tier configuration hierarchy for maximum flexibility
- Safe defaults for production

## Quick Start

### 1. Install the Subsystem

Download and install the feature pack into your WildFly installation:

```bash
# Using Galleon
galleon.sh install com.github.wildfly.flyway:wildfly-flyway-feature-pack:1.0.0
```

### 2. Configure WildFly

Add the subsystem to your `standalone.xml`:

```xml
<extension module="com.github.wildfly.flyway"/>

<subsystem xmlns="urn:wildfly:flyway:1.0"/>
```

### 3. Add Migration Scripts

Create SQL migration scripts in your application at `src/main/resources/db/migration/`:

```sql
-- V1__Create_person_table.sql
CREATE TABLE person (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- V2__Add_email_column.sql
ALTER TABLE person ADD COLUMN email VARCHAR(255);
```

### 4. Configure Your Application

Create `src/main/resources/META-INF/flyway.properties`:

```properties
spring.flyway.datasource=java:jboss/datasources/MyAppDS
```

**Note:** Replace `MyAppDS` with your actual datasource JNDI name. Never use ExampleDS in production!

### 5. Deploy

Deploy your application - migrations run automatically!

## Configuration Guide

### Configuration Hierarchy

The subsystem uses a three-level configuration hierarchy:

1. **Base Configuration**: Subsystem configuration in `standalone.xml` provides defaults for ALL deployments
2. **Application Overrides**: Properties in `META-INF/flyway.properties` override specific subsystem defaults
3. **Datasource Resolution**: 
   - From application properties (highest priority)
   - From subsystem `default-datasource`
   - Auto-discovery (only if explicitly enabled)

### Application Configuration

Configure Flyway per application using `META-INF/flyway.properties`:

```properties
# Required - specify which datasource to use
spring.flyway.datasource=java:jboss/datasources/MyDS

# Optional settings with defaults
spring.flyway.baseline-on-migrate=false
spring.flyway.clean-disabled=true
spring.flyway.validate-on-migrate=true
spring.flyway.locations=classpath:db/migration
spring.flyway.table=flyway_schema_history

# Both namespaces supported (flyway.* takes precedence)
flyway.datasource=java:jboss/datasources/MyDS
```

### Subsystem Configuration

Configure global defaults in `standalone.xml` that apply to ALL deployments:

```xml
<subsystem xmlns="urn:wildfly:flyway:1.0"
           enabled="true"
           default-datasource="java:jboss/datasources/DefaultDS"
           baseline-on-migrate="false"
           clean-disabled="true"
           validate-on-migrate="true"
           locations="classpath:db/migration"
           table="flyway_schema_history"/>
```

**Important:** 
- These settings provide the base configuration for all deployments
- Applications inherit these defaults automatically
- Applications can override individual settings in their `flyway.properties`
- If `default-datasource` is set, applications don't need to specify a datasource (unless they want a different one)

### Environment Variables and Expression Resolution

The subsystem supports full WildFly expression resolution, including environment variables and system properties:

#### In Subsystem Configuration (standalone.xml)

```xml
<subsystem xmlns="urn:wildfly:flyway:1.0"
           enabled="${env.FLYWAY_ENABLED:true}"
           default-datasource="${env.FLYWAY_DATASOURCE:}"
           baseline-on-migrate="${env.FLYWAY_BASELINE_ON_MIGRATE:false}"
           clean-disabled="${env.FLYWAY_CLEAN_DISABLED:true}"
           locations="${env.FLYWAY_LOCATIONS:classpath:db/migration}"/>
```

#### In Deployment Properties (META-INF/flyway.properties)

```properties
# Environment variable expressions with defaults
spring.flyway.datasource=${env.FLYWAY_DATASOURCE:java:jboss/datasources/DefaultDS}
spring.flyway.baseline-on-migrate=${env.FLYWAY_BASELINE:false}
spring.flyway.locations=${env.FLYWAY_LOCATIONS:classpath:db/migration}

# System property expressions
spring.flyway.table=${flyway.table:flyway_schema_history}
spring.flyway.validate-on-migrate=${validate.migrations:true}
```

#### Setting Environment Variables

```bash
# Set environment variables before starting WildFly
export FLYWAY_ENABLED=true
export FLYWAY_DATASOURCE=java:jboss/datasources/PostgresDS
export FLYWAY_BASELINE_ON_MIGRATE=true
export FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/patches

# Or use system properties
./bin/standalone.sh -Dflyway.table=schema_version -Dvalidate.migrations=false
```

**Expression Syntax:**
- `${env.VARIABLE_NAME}` - Environment variable (fails if not set)
- `${env.VARIABLE_NAME:defaultValue}` - Environment variable with default
- `${sys.PROPERTY_NAME:defaultValue}` - System property with default  
- `${PROPERTY_NAME:defaultValue}` - System property (shorthand)

**Important:** Expression resolution works in both subsystem configuration and deployment properties files, providing maximum flexibility for cloud-native deployments.

### Configuration Properties Reference

| Property | Description | Default |
|----------|-------------|---------|
| `datasource` | JNDI name of the datasource | *(required)* |
| `enabled` | Enable/disable Flyway | `true` |
| `baseline-on-migrate` | Create baseline for existing databases | `false` |
| `clean-disabled` | Disable destructive clean operations | `true` |
| `validate-on-migrate` | Validate migrations before applying | `true` |
| `locations` | Where to find migration scripts | `classpath:db/migration` |
| `table` | Name of schema history table | `flyway_schema_history` |

## Common Use Cases

### Central Configuration for All Applications

Configure defaults in `standalone.xml` once:

```xml
<subsystem xmlns="urn:wildfly:flyway:1.0"
           default-datasource="${env.DB_DATASOURCE:java:jboss/datasources/PostgresDS}"
           baseline-on-migrate="${env.DB_BASELINE:false}"
           locations="${env.DB_MIGRATION_PATH:classpath:db/migration}"/>
```

Now all applications automatically use these settings without needing their own configuration!

### Application-Specific Override

Application needs a different datasource than the subsystem default:

```properties
# META-INF/flyway.properties
spring.flyway.datasource=java:jboss/datasources/AppSpecificDS
# All other settings inherited from subsystem
```

### Multiple Environments

Use environment-specific datasources:

```properties
# META-INF/flyway.properties
spring.flyway.datasource=${env.DB_JNDI}
spring.flyway.baseline-on-migrate=${env.DB_BASELINE_ON_MIGRATE:false}
spring.flyway.locations=${env.DB_MIGRATION_PATH:classpath:db/migration}
```

Or configure in the subsystem with required environment variables:

```xml
<subsystem xmlns="urn:wildfly:flyway:1.0"
           default-datasource="${env.DB_DATASOURCE}"/>
```

This ensures the datasource MUST be explicitly configured via environment variable.

### Existing Database

Enable baseline for databases with existing schema:

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

### Database-Specific Migrations

Use vendor placeholders for database-specific SQL:

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/migration/{vendor}
```

Directory structure:
```
db/migration/
├── V1__Common_schema.sql
├── postgresql/
│   └── V2__PostgreSQL_specific.sql
└── mysql/
    └── V2__MySQL_specific.sql
```

### Disable for Specific Deployment

```properties
# META-INF/flyway.properties
spring.flyway.enabled=false
```

## Monitoring and Operations

### Check Migration Status

Use JBoss CLI to check the subsystem status:

```bash
/subsystem=flyway:read-resource(include-runtime=true)
```

### View Logs

Flyway operations are logged to the server log:

```
INFO  [com.github.wildfly.flyway] Flyway enabled for deployment 'myapp.war' using datasource 'java:jboss/datasources/MyDS' from deployment properties
INFO  [com.github.wildfly.flyway] Found 3 pending migrations for deployment: myapp.war
INFO  [com.github.wildfly.flyway] Successfully executed 3 migrations for deployment: myapp.war
```

## Troubleshooting

### No Migrations Running

1. **Check if migrations exist**: Ensure files are in `db/migration/` 
2. **Verify datasource**: Check JNDI name matches exactly
3. **Check enabled status**: Verify not disabled in properties
4. **Review logs**: Look for "No Flyway datasource configured"

### Migration Failures

1. **Check SQL syntax**: Test scripts manually first
2. **Verify permissions**: Ensure datasource user has DDL permissions
3. **Check history table**: May need manual cleanup after failures
4. **Enable debug logging**: Add to `standalone.xml`:
   ```xml
   <logger category="com.github.wildfly.flyway">
       <level name="DEBUG"/>
   </logger>
   ```

### Configuration Not Applied

Remember the precedence order:
1. Application properties override everything
2. Subsystem configuration provides defaults
3. Both `flyway.*` and `spring.flyway.*` properties work

## Security Considerations

- **Clean operations disabled by default** - protects against accidental data loss
- **Validation enabled by default** - ensures migration integrity
- **JNDI names validated** - prevents injection attacks
- **SQL scripts run with datasource permissions** - ensure appropriate database user

## License

Apache License 2.0