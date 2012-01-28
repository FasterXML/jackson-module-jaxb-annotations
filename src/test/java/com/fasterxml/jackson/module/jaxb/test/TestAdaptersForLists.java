package com.fasterxml.jackson.module.jaxb.test;

import java.util.*;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.test.TestAdapters.Bean;
import com.fasterxml.jackson.module.jaxb.test.TestAdapters.SillyAdapter;

/**
 * Unit tests to fix [JACKSON-722]
 */
public class TestAdaptersForLists extends BaseJaxbTest
{
    public static class SillyAdapter extends XmlAdapter<String, Date>
    {
        public SillyAdapter() { }

        @Override
        public Date unmarshal(String date) throws Exception {
            return new Date(29L);
        }

        @Override
        public String marshal(Date date) throws Exception {
            return "XXX";
        }
    }

    static class Wrapper {
        @XmlJavaTypeAdapter(SillyAdapter.class)
        public List<Date> values;

        public Wrapper() { }
        public Wrapper(long l) {
            values = new ArrayList<Date>();
            values.add(new Date(l));
        }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
 
    public void testAdapterForList() throws Exception
    {
        Wrapper w = new Wrapper(123L);
        assertEquals("[\"XXX\"]", getJaxbMapper().writeValueAsString(w));
    }

    public void testSimpleAdapterDeserialization() throws Exception
    {
        Wrapper w = getJaxbMapper().readValue("{\"values\":[\"abc\"]}", Wrapper.class);
        assertNotNull(w);
        assertNotNull(w.values);
        assertEquals(1, w.values.size());
        assertEquals(29L, w.values.get(0).getTime());
    }

}
