package com.fasterxml.jackson.module.jaxb.misc;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Unit tests to ensure that handling of writing of null properties (or not)
 * works when using JAXB annotation introspector.
 * Mostly to test out [JACKSON-309].
 */
public class TestJaxbNullProperties
    extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    public static class Bean
    {
       public String empty;

       public String x = "y";
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
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();
    
    // Test for [JACKSON-256], thanks John.
    public void testWriteNulls() throws Exception
    {
        BeanWithNillable bean = new BeanWithNillable();
        bean.X = new Nillable();
        assertEquals("{\"X\":{\"Z\":null}}", MAPPER.writeValueAsString(bean));
    }

    // Testing [JACKSON-309]
    public void testNullProps() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        assertEquals("{\"x\":\"y\"}", mapper.writeValueAsString(new Bean()));
    }
}
