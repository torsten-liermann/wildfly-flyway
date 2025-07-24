package com.github.wildfly.flyway.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;

import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for Flyway subsystem configuration.
 */
final class FlywaySubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

    static final FlywaySubsystemParser INSTANCE = new FlywaySubsystemParser();

    private FlywaySubsystemParser() {
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, FlywayExtension.SUBSYSTEM_NAME);
        address.protect();

        ModelNode subsystemAdd = new ModelNode();
        subsystemAdd.get(OP).set(ADD);
        subsystemAdd.get(OP_ADDR).set(address);
        
        // Parse attributes if present
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attrName = reader.getAttributeLocalName(i);
            String attrValue = reader.getAttributeValue(i);
            
            if ("enabled".equals(attrName)) {
                subsystemAdd.get("enabled").set(attrValue);
            } else if ("default-datasource".equals(attrName)) {
                subsystemAdd.get("default-datasource").set(attrValue);
            }
        }
        
        operations.add(subsystemAdd);

        // For now, we have a simple subsystem with no child elements
        requireNoContent(reader);
    }
}
