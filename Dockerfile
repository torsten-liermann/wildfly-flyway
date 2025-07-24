FROM quay.io/wildfly/wildfly:28.0.1.Final-jdk11

# Copy the feature pack
COPY feature-pack/target/wildfly-flyway-feature-pack-*.zip /tmp/

# Install the feature pack using Galleon
RUN cd /tmp && \
    unzip wildfly-flyway-feature-pack-*.zip && \
    $JBOSS_HOME/bin/jboss-cli.sh --command="module add --name=org.flywaydb.core --resources=/tmp/flyway-core-*.jar" && \
    $JBOSS_HOME/bin/jboss-cli.sh --command="module add --name=com.github.wildfly.flyway --resources=/tmp/wildfly-flyway-subsystem-*.jar --dependencies=org.flywaydb.core,org.jboss.as.controller,org.jboss.as.server,javax.api,org.jboss.logging,org.wildfly.extension.undertow" && \
    rm -rf /tmp/*

# Add the subsystem to standalone configuration
RUN $JBOSS_HOME/bin/jboss-cli.sh --file=/opt/jboss/wildfly/bin/add-flyway-subsystem.cli

# Create CLI script for adding the subsystem
RUN echo 'embed-server --std-out=echo --server-config=standalone.xml\n\
/extension=com.github.wildfly.flyway:add(module=com.github.wildfly.flyway)\n\
/subsystem=flyway:add\n\
stop-embedded-server' > /opt/jboss/wildfly/bin/add-flyway-subsystem.cli

# Environment variables for Spring Boot style configuration
ENV SPRING_FLYWAY_ENABLED=true \
    SPRING_FLYWAY_LOCATIONS=classpath:db/migration \
    SPRING_FLYWAY_BASELINE_ON_MIGRATE=false \
    SPRING_FLYWAY_CLEAN_DISABLED=true

# Expose management and application ports
EXPOSE 8080 9990

# Run WildFly
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]