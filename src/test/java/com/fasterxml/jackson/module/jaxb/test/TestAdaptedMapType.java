package com.fasterxml.jackson.module.jaxb.test;

import java.io.*;
import java.util.HashMap;

import junit.framework.TestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

/**
 * @author Ryan Heaton
 */
public class TestAdaptedMapType extends TestCase {

  public void testJacksonAdaptedMapType() throws IOException {
    ObjectContainingAMap obj = new ObjectContainingAMap();
    obj.setMyMap(new HashMap<String, String>());
    obj.getMyMap().put("this", "that");
    obj.getMyMap().put("how", "here");

    ObjectMapper mapper = new ObjectMapper();
    mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
    byte[] json = mapper.writeValueAsBytes(obj);
    obj = mapper.readValue(new ByteArrayInputStream(json), ObjectContainingAMap.class);
    assertNotNull(obj.getMyMap());
  }
}
