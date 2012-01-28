package com.fasterxml.jackson.module.jaxb.test;

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

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // Testing [JACKSON-309]
     public void testNullProps() throws Exception
     {
         ObjectMapper mapper = getJaxbMapper();
         mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
         assertEquals("{\"x\":\"y\"}", mapper.writeValueAsString(new Bean()));
     }
}
