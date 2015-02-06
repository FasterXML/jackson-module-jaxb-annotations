package com.fasterxml.jackson.module.jaxb.misc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

public class TestSerializationInclusion extends BaseJaxbTest
{
    static class Data {
        private final List<Object> stuff = new java.util.ArrayList<Object>();

        public List<Object> getStuff() {
            return stuff;
        }
    }    

    public void testIssue39() throws Exception
    {
        // First: use plain JAXB introspector:
        _testInclusion(getJaxbMapper());
        // and then combination ones
        _testInclusion(getJaxbAndJacksonMapper());
        _testInclusion(getJacksonAndJaxbMapper());

        // finally: verify using actual module
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JaxbAnnotationModule());
        _testInclusion(mapper);
    }
        
    private void _testInclusion(ObjectMapper mapper) throws Exception
    {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        String json = mapper.writeValueAsString(new Data());
        assertEquals("{}", json);
    }
}
