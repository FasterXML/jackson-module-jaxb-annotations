package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.StringWriter;
import java.util.*;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.type.TypeFactory;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

/**
 * Tests for verifying that JAXB annotation based introspector
 * implementation works as expected
 *
 * @author Ryan Heaton
 * @author Tatu Saloranta
 */
public class TestJaxbAnnotationIntrospector
    extends BaseJaxbTest
{
    /*
    /****************************************************
    /* Helper beans
    /****************************************************
     */

    public static enum EnumExample {

        @XmlEnumValue("Value One")
        VALUE1
    }

    public static class JaxbExample
    {

        private String attributeProperty;
        private String elementProperty;
        private List<String> wrappedElementProperty;
        private EnumExample enumProperty;
        private QName qname;
        private QName qname1;
        private String propertyToIgnore;

        @XmlJavaTypeAdapter(QNameAdapter.class)
        public QName getQname()
        {
            return qname;
        }

        public void setQname(QName qname)
        {
            this.qname = qname;
        }

        public QName getQname1()
        {
            return qname1;
        }

        public void setQname1(QName qname1)
        {
            this.qname1 = qname1;
        }

        @XmlAttribute(name="myattribute")
        public String getAttributeProperty()
        {
            return attributeProperty;
        }

        public void setAttributeProperty(String attributeProperty)
        {
            this.attributeProperty = attributeProperty;
        }

        @XmlElement(name="myelement")
        public String getElementProperty()
        {
            return elementProperty;
        }

        public void setElementProperty(String elementProperty)
        {
            this.elementProperty = elementProperty;
        }

        @XmlElementWrapper(name="mywrapped")
        public List<String> getWrappedElementProperty()
        {
            return wrappedElementProperty;
        }

        public void setWrappedElementProperty(List<String> wrappedElementProperty)
        {
            this.wrappedElementProperty = wrappedElementProperty;
        }

        public EnumExample getEnumProperty()
        {
            return enumProperty;
        }

        public void setEnumProperty(EnumExample enumProperty)
        {
            this.enumProperty = enumProperty;
        }

        @XmlTransient
        public String getPropertyToIgnore()
        {
            return propertyToIgnore;
        }

        public void setPropertyToIgnore(String propertyToIgnore)
        {
            this.propertyToIgnore = propertyToIgnore;
        }
    }

    public static class QNameAdapter extends XmlAdapter<String, QName> {

        @Override
        public QName unmarshal(String v) throws Exception
        {
            return QName.valueOf(v);
        }

        @Override
        public String marshal(QName v) throws Exception
        {
            return (v == null) ? null : v.toString();
        }
    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    public static class SimpleBean
    {
        @XmlElement
        protected String jaxb = "1";

        @XmlElement
        protected String jaxb2 = "2";

        @SuppressWarnings("unused")
        @XmlElement(name="jaxb3")
        private String oddName = "3";

        public String notAGetter() { return "xyz"; }

        @XmlTransient
        public int foobar = 3;
    }

    @SuppressWarnings("unused")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SimpleBean2 {

        protected String jaxb = "1";
        private String jaxb2 = "2";
        @XmlElement(name="jaxb3")
        private String oddName = "3";

    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    @XmlRootElement(namespace="urn:class")
    static class NamespaceBean
    {
        @XmlElement(namespace="urn:method")
        public String string;
    }

    @XmlRootElement(name="test")
    static class RootNameBean { }

    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    public static class AlphaBean
    {
        public int c = 3;
        public int a = 1;
        public int b = 2;
    }
    
    public static class KeyValuePair {
    	private String key;
    	private String value;
    	public KeyValuePair() {}
		public String getKey() {
			return key;
		}
		public void setKey(String key) {
			this.key = key;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		};
    }

    // Beans for [JACKSON-256]
    
    @XmlRootElement
    static class BeanWithNillable {
        public Nillable X;
    }

    @XmlRootElement
    static class Nillable {
        @XmlElement (name="Z", nillable=true)
        Integer Z;
    } 

    /*
    /****************************************************
    /* Unit tests
    /****************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();
    
    public void testDetection() throws Exception
    {
        Map<String,Object> result = writeAndMap(MAPPER, new SimpleBean());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jaxb"));
        assertEquals("2", result.get("jaxb2"));
        assertEquals("3", result.get("jaxb3"));

        result = writeAndMap(MAPPER, new SimpleBean2());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jaxb"));
        assertEquals("2", result.get("jaxb2"));
        assertEquals("3", result.get("jaxb3"));
    }

    /**
     * tests getting serializer/deserializer instances.
     */
    public void testSerializeDeserializeWithJaxbAnnotations() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        JaxbExample ex = new JaxbExample();
        QName qname = new QName("urn:hi", "hello");
        ex.setQname(qname);
        QName qname1 = new QName("urn:hi", "hello1");
        ex.setQname1(qname1);
        ex.setAttributeProperty("attributeValue");
        ex.setElementProperty("elementValue");
        ex.setWrappedElementProperty(Arrays.asList("wrappedElementValue"));
        ex.setEnumProperty(EnumExample.VALUE1);
        ex.setPropertyToIgnore("ignored");
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, ex);
        writer.flush();
        writer.close();

        String json = writer.toString();

        // uncomment to see what the json looks like.
        // System.out.println(json);

        //make sure the json is written out correctly.
        JsonNode node = mapper.readValue(json, JsonNode.class);
        assertEquals(qname.toString(), node.get("qname").asText());
        JsonNode attr = node.get("myattribute");
        assertNotNull(attr);
        assertEquals("attributeValue", attr.asText());
        assertEquals("elementValue", node.get("myelement").asText());
        assertEquals(1, node.get("mywrapped").size());
        assertEquals("wrappedElementValue", node.get("mywrapped").get(0).asText());
        assertEquals("Value One", node.get("enumProperty").asText());
        assertNull(node.get("propertyToIgnore"));
        
        //now make sure it gets deserialized correctly.
        JaxbExample readEx = mapper.readValue(json, JaxbExample.class);
        assertEquals(ex.qname, readEx.qname);
        assertEquals(ex.qname1, readEx.qname1);
        assertEquals(ex.attributeProperty, readEx.attributeProperty);
        assertEquals(ex.elementProperty, readEx.elementProperty);
        assertEquals(ex.wrappedElementProperty, readEx.wrappedElementProperty);
        assertEquals(ex.enumProperty, readEx.enumProperty);
        assertNull(readEx.propertyToIgnore);
    }

    public void testRootNameAccess() throws Exception
    {
        AnnotationIntrospector ai = new JaxbAnnotationIntrospector(TypeFactory.defaultInstance());
        // If no @XmlRootElement, should get null (unless pkg has etc)
        assertNull(ai.findRootName(AnnotatedClass.construct(SimpleBean.class, ai, null)));
        // With @XmlRootElement, but no name, empty String
        assertEquals("", ai.findRootName(AnnotatedClass.construct(NamespaceBean.class, ai, null)));
        // and otherwise explicit name
        assertEquals("test", ai.findRootName(AnnotatedClass.construct(RootNameBean.class, ai, null)));
    }

    // JAXB can specify that properties are to be written in alphabetic order...
    public void testSerializationAlphaOrdering() throws Exception
    {
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", MAPPER.writeValueAsString(new AlphaBean()));
    }

    // Test for [JACKSON-256], thanks John.
    public void testWriteNulls() throws Exception
    {
        BeanWithNillable bean = new BeanWithNillable();
        bean.X = new Nillable();
        assertEquals("{\"X\":{\"Z\":null}}", MAPPER.writeValueAsString(bean));
    }
}
