/**
 * Package info can be used to add "package annotations", so here we are...
 */
@javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters({
  @javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter(
    type = javax.xml.namespace.QName.class,
    value = com.fasterxml.jackson.module.jaxb.test.TestJaxbAnnotationIntrospector.QNameAdapter.class
  )
})
package com.fasterxml.jackson.module.jaxb.test;

