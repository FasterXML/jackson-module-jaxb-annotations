package com.fasterxml.jackson.module.jaxb.test;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public class TestVersions extends BaseJaxbTest
{
    /**
     * Not a good to do this, but has to do, for now...
     */
    private final static int MAJOR_VERSION = 2;
    private final static int MINOR_VERSION = 0;

    public void testMapperVersions()
    {
        JaxbAnnotationIntrospector f = new JaxbAnnotationIntrospector();
        assertVersion(f.version(), MAJOR_VERSION, MINOR_VERSION);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void assertVersion(Version v, int major, int minor)
    {
        assertFalse("Should find version information (got "+v+")", v.isUknownVersion());
        assertEquals(major, v.getMajorVersion());
        assertEquals(minor, v.getMinorVersion());
        // Check patch level initially, comment out for maint versions
        assertEquals(0, v.getPatchLevel());
    }
}

