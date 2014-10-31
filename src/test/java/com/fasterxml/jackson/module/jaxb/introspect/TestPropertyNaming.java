package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.IOException;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestPropertyNaming
    extends BaseJaxbTest
{
    static class WithXmlValueNoOverride
    {
        @XmlValue
        public int getFoobar() {
            return 13;
        }
    }

    static class WithXmlValueAndOverride
    {
        @XmlValue
        @JsonProperty("number")
        public int getFoobar() {
            return 13;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // For [Issue#30]
    public void testXmlValueDefault() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"value\":13}", mapper.writeValueAsString(new WithXmlValueNoOverride()));
    }

    // For [Issue#30]
    public void testXmlValueOverride() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        // default is 'value'
        assertEquals("{\"number\":13}", mapper.writeValueAsString(new WithXmlValueAndOverride()));
    }
}
