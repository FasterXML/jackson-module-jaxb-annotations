package com.fasterxml.jackson.module.jaxb.test;

import junit.framework.TestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;

/**
 * @author Ryan Heaton
 */
public class TestAdaptedMapType extends TestCase {

  public void testJacksonAdaptedMapType() throws Exception {
    ObjectContainingAMap obj = new ObjectContainingAMap();
    obj.setMyMap(new HashMap<String, String>());
    obj.getMyMap().put("this", "that");
    obj.getMyMap().put("how", "here");

    ObjectMapper mapper = new ObjectMapper();
    mapper.getDeserializationConfig().withAnnotationIntrospector(new JaxbAnnotationIntrospector());
    mapper.getSerializationConfig().withAnnotationIntrospector(new JaxbAnnotationIntrospector());
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    mapper.writeValue(bytesOut, obj);
    obj = mapper.readValue(new ByteArrayInputStream(bytesOut.toByteArray()), ObjectContainingAMap.class);
    assertNotNull(obj.getMyMap());
  }
}
