package com.github.wildfly.flyway.extension;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Writer for Flyway subsystem configuration.
 *
 * Uses {@link AttributeDefinition#marshallAsAttribute(ModelNode, XMLStreamWriter)} for each
 * attribute so that expression values are persisted unchanged and default values are
 * skipped consistently. Calling {@code asBoolean()} on an EXPRESSION-typed ModelNode
 * throws {@code IllegalArgumentException}, so the previous boolean-evaluating writer
 * could not persist boolean attributes that held expressions like
 * {@code ${env.FLYWAY_ENABLED:true}}.
 */
final class FlywaySubsystemWriter implements XMLStreamConstants, XMLElementWriter<SubsystemMarshallingContext> {

    static final FlywaySubsystemWriter INSTANCE = new FlywaySubsystemWriter();

    private static final AttributeDefinition[] ATTRIBUTES = {
            FlywaySubsystemDefinition.ENABLED,
            FlywaySubsystemDefinition.DEFAULT_DATASOURCE,
            FlywaySubsystemDefinition.BASELINE_ON_MIGRATE,
            FlywaySubsystemDefinition.CLEAN_DISABLED,
            FlywaySubsystemDefinition.VALIDATE_ON_MIGRATE,
            FlywaySubsystemDefinition.LOCATIONS,
            FlywaySubsystemDefinition.TABLE,
    };

    private FlywaySubsystemWriter() {
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(FlywaySubsystemNamespace.FLYWAY_1_0.getUriString(), false);

        ModelNode node = context.getModelNode();
        for (AttributeDefinition attribute : ATTRIBUTES) {
            attribute.getMarshaller().marshallAsAttribute(attribute, node, false, writer);
        }

        writer.writeEndElement();
    }
}
