package com.fasterxml.jackson.module.jaxb.test;

import java.io.*;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * @author Ryan Heaton
 */
public class TestAdaptedMapType extends BaseJaxbTest
{

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
