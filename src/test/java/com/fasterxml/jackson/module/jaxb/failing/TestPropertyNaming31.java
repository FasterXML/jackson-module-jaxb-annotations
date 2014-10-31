package com.fasterxml.jackson.module.jaxb.failing;

import java.io.IOException;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestPropertyNaming31
    extends BaseJaxbTest
{
    // [Issue#31]
    static class Query {
        @XmlValue
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(final String pQuery) {
            query = pQuery;
        }
    }    

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // For [Issue#31]
    public void testXmlValueDefault2() throws IOException
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();
        
        Query q2 = new Query();
        q2.query = "foo";
        
        // default is 'value'
        Query q = mapper.readValue("{\"value\":\"some stuff\"}", Query.class);
        assertEquals("some stuff", q.getQuery());
    }
}
