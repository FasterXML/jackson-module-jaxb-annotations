package com.fasterxml.jackson.module.jaxb.test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * @author Ryan Heaton
 */
public class TestAdaptedMapType extends BaseJaxbTest
{
    static class ObjectContainingAMap {

        private Map<String, String> myMap;

        @XmlJavaTypeAdapter( MapAdapter.class )
        public Map<String, String> getMyMap() {
          return myMap;
        }

        public void setMyMap(Map<String, String> myMap) {
          this.myMap = myMap;
        }
    }
    
    public void testJacksonAdaptedMapType() throws IOException {
        ObjectContainingAMap obj = new ObjectContainingAMap();
        obj.setMyMap(new HashMap<String, String>());
        obj.getMyMap().put("this", "that");
        obj.getMyMap().put("how", "here");

        ObjectMapper mapper = getJaxbMapper();
        byte[] json = mapper.writeValueAsBytes(obj);
        obj = mapper.readValue(new ByteArrayInputStream(json), ObjectContainingAMap.class);
        assertNotNull(obj.getMyMap());
    }
}
