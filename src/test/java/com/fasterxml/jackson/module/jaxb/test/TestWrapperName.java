package com.fasterxml.jackson.module.jaxb.test;

import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * For [Issue#13]; allow forced renaming of properties
 * with wrapper.
 */
public class TestWrapperName extends BaseJaxbTest
{
    static class Bean
    {
        @XmlElementWrapper(name="wrap")
        public int id;
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testWrapperRenaming() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        // verify that by default feature is off:
        assertFalse(mapper.isEnabled(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME));
        Bean input = new Bean();
        input.id = 3;
        assertEquals("{\"id\":3}", mapper.writeValueAsString(input));
        // but if we create new instance, configure
        mapper = getJaxbMapper();
        mapper.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        assertEquals("{\"wrap\":3}", mapper.writeValueAsString(input));
    }
}
