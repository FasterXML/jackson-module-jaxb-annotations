package com.fasterxml.jackson.module.jaxb.id;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

// Reproduction of [Issue-9]
public class TestXmlID2 extends BaseJaxbTest
{
    @XmlRootElement(name = "department")
    @XmlAccessorType(XmlAccessType.FIELD)
    public class Department {
        @XmlElement
        @XmlID
        public Long id;

        public String name;

        @XmlIDREF
        public List<User> employees = new ArrayList<User>();
        
        public void setId(Long id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setEmployees(List<User> employees) {
            this.employees = employees;
        }
    }
    
    
    @XmlRootElement(name = "user")
    @XmlAccessorType(XmlAccessType.FIELD)
    public class User
    {
        @XmlElement @XmlID
        public Long id;

        public String username;
        public String email;

        @XmlIDREF
        public Department department;
            
        public void setId(Long id) {
            this.id = id;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setDepartment(Department department) {
            this.department = department;
        }
    }       
    
    private List<User> getUserList()
    {
        List<User> resultList = new ArrayList<User>();
        List<User> users = new java.util.ArrayList<User>();

        User user1, user2, user3;
        Department dep;
        user1 = new User();
        user1.setId(11L);
        user1.setUsername("11");
        user1.setEmail("11@test.com");
        user2 = new User();
        user2.setId(22L);
        user2.setUsername("22");
        user2.setEmail("22@test.com");
        user3 = new User();
        user3.setId(33L);
        user3.setUsername("33");
        user3.setEmail("33@test.com");

        dep = new Department();
        dep.setId(9L);
        dep.setName("department9");
        user1.setDepartment(dep);
        users.add(user1);
        user2.setDepartment(dep);
        users.add(user2);

        dep.setEmployees(users);
        resultList.clear();
        resultList.add(user1);
        resultList.add(user2);
        resultList.add(user3);
        return resultList;
    }
    
    public void testIdWithJaxb() throws Exception
    {
        String expected = "[{\"id\":11,\"username\":\"11\",\"email\":\"11@test.com\",\"department\":9},{\"id\":22,\"username\":\"22\",\"email\":\"22@test.com\",\"department\":9}]}},{\"id\":22,\"username\":\"22\",\"email\":\"22@test.com\",\"department\":9},{\"id\":33,\"username\":\"33\",\"email\":\"33@test.com\",\"department\":null}]";
        ObjectMapper mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector(mapper.getTypeFactory()));
        List<User> users = getUserList();
        System.out.println(mapper.writeValueAsString(users));
        assertEquals(expected,mapper.writeValueAsString(users));
    }
}
