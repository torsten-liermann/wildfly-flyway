package com.github.wildfly.flyway.extension;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Writer for Flyway subsystem configuration.
 */
final class FlywaySubsystemWriter implements XMLStreamConstants, XMLElementWriter<SubsystemMarshallingContext> {

    static final FlywaySubsystemWriter INSTANCE = new FlywaySubsystemWriter();

    private FlywaySubsystemWriter() {
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(FlywaySubsystemNamespace.FLYWAY_1_0.getUriString(), false);
        
        ModelNode node = context.getModelNode();
        
        // Write enabled attribute if defined and not default
        if (node.hasDefined("enabled") && !node.get("enabled").asBoolean()) {
            writer.writeAttribute("enabled", "false");
        }
        
        // Write default-datasource attribute if defined and not default
        if (node.hasDefined("default-datasource")) {
            String datasource = node.get("default-datasource").asString();
            if (!"ExampleDS".equals(datasource)) {
                writer.writeAttribute("default-datasource", datasource);
            }
        }
        
        // Write baseline-on-migrate attribute if defined and not default
        if (node.hasDefined("baseline-on-migrate") && node.get("baseline-on-migrate").asBoolean()) {
            writer.writeAttribute("baseline-on-migrate", "true");
        }
        
        // Write clean-disabled attribute if defined and not default
        if (node.hasDefined("clean-disabled") && !node.get("clean-disabled").asBoolean()) {
            writer.writeAttribute("clean-disabled", "false");
        }
        
        // Write validate-on-migrate attribute if defined and not default
        if (node.hasDefined("validate-on-migrate") && !node.get("validate-on-migrate").asBoolean()) {
            writer.writeAttribute("validate-on-migrate", "false");
        }
        
        // Write locations attribute if defined and not default
        if (node.hasDefined("locations")) {
            String locations = node.get("locations").asString();
            if (!"classpath:db/migration".equals(locations)) {
                writer.writeAttribute("locations", locations);
            }
        }
        
        // Write table attribute if defined and not default
        if (node.hasDefined("table")) {
            String table = node.get("table").asString();
            if (!"flyway_schema_history".equals(table)) {
                writer.writeAttribute("table", table);
            }
        }
        
        writer.writeEndElement();
    }
}
