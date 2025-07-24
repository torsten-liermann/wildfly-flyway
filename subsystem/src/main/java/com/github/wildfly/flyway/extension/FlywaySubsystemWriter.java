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
        
        writer.writeEndElement();
    }
}
