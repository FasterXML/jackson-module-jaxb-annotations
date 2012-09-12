package com.fasterxml.jackson.module.jaxb.test;

import java.util.*;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Unit tests to verify handling of @XmlElementWrapper annotation.
 */
public class TestElementWrapper extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    // Beans for [JACKSON-436]
    static class Person {
        @XmlElementWrapper(name="phones")
        @XmlElement(type=Phone.class)
        public Collection<IPhone> phone;
    }

    interface IPhone {
        public String getNumber();
    }

    static class Phone implements IPhone
    {
        private String number;

        public Phone() { }
        
        public Phone(String number) { this.number = number; }
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // [JACKSON-436]
    public void testWrapperWithCollection() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        // for fun, force renaming with wrapper annotation, even for JSON
        mapper.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        Collection<IPhone> phones = new HashSet<IPhone>();
        phones.add(new Phone("555-6666"));
        Person p = new Person();
        p.phone = phones;

        String json = mapper.writeValueAsString(p);

        // as per 
        assertEquals("{\"phones\":[{\"number\":\"555-6666\"}]}", json);

//        System.out.println("JSON == "+json);

        Person result = mapper.readValue(json, Person.class);
        assertNotNull(result.phone);
        assertEquals(1, result.phone.size());
        assertEquals("555-6666", result.phone.iterator().next().getNumber());
    }
}
