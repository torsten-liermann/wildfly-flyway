package com.github.wildfly.flyway.extension;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for Flyway subsystem XML parsing.
 */
public class FlywaySubsystemParsingTest {
    
    private static final String NAMESPACE = "urn:wildfly:flyway:1.0";
    
    @Test
    public void testMinimalSubsystem() throws Exception {
        String xml = "<subsystem xmlns=\"" + NAMESPACE + "\" />";
        List<ModelNode> operations = parse(xml);
        
        assertEquals("Should have one operation", 1, operations.size());
        
        ModelNode addOp = operations.get(0);
        assertEquals("Should be ADD operation", "add", addOp.get("operation").asString());
        assertEquals("Should be for flyway subsystem", "flyway", 
                addOp.get("address").get(0).get("subsystem").asString());
    }
    
    @Test
    public void testSubsystemWithEnabled() throws Exception {
        String xml = "<subsystem xmlns=\"" + NAMESPACE + "\" enabled=\"false\" />";
        List<ModelNode> operations = parse(xml);
        
        assertEquals("Should have one operation", 1, operations.size());
        
        ModelNode addOp = operations.get(0);
        assertEquals("Should be ADD operation", "add", addOp.get("operation").asString());
        assertTrue("Should have enabled attribute", addOp.has("enabled"));
        assertEquals("Enabled should be false", "false", addOp.get("enabled").asString());
    }
    
    @Test
    public void testSubsystemWithDefaultDatasource() throws Exception {
        String xml = "<subsystem xmlns=\"" + NAMESPACE + "\" default-datasource=\"java:jboss/datasources/TestDS\" />";
        List<ModelNode> operations = parse(xml);
        
        assertEquals("Should have one operation", 1, operations.size());
        
        ModelNode addOp = operations.get(0);
        assertEquals("Should be ADD operation", "add", addOp.get("operation").asString());
        assertTrue("Should have default-datasource attribute", addOp.has("default-datasource"));
        assertEquals("Default datasource should match", "java:jboss/datasources/TestDS", 
                addOp.get("default-datasource").asString());
    }
    
    @Test
    public void testSubsystemWithAllAttributes() throws Exception {
        String xml = "<subsystem xmlns=\"" + NAMESPACE + "\" " +
                "enabled=\"true\" " +
                "default-datasource=\"java:jboss/datasources/MyDS\" />";
        List<ModelNode> operations = parse(xml);
        
        assertEquals("Should have one operation", 1, operations.size());
        
        ModelNode addOp = operations.get(0);
        assertEquals("Should be ADD operation", "add", addOp.get("operation").asString());
        assertTrue("Should have enabled attribute", addOp.has("enabled"));
        assertEquals("Enabled should be true", "true", addOp.get("enabled").asString());
        assertTrue("Should have default-datasource attribute", addOp.has("default-datasource"));
        assertEquals("Default datasource should match", "java:jboss/datasources/MyDS", 
                addOp.get("default-datasource").asString());
    }
    
    private List<ModelNode> parse(String xml) throws Exception {
        XMLMapper mapper = XMLMapper.Factory.create();
        QName rootElement = new QName(FlywaySubsystemNamespace.FLYWAY_1_0.getUriString(), "subsystem");
        mapper.registerRootElement(rootElement, FlywaySubsystemParser.INSTANCE);
        
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xml));
        
        List<ModelNode> operations = new ArrayList<>();
        mapper.parseDocument(operations, reader);
        
        return operations;
    }
}