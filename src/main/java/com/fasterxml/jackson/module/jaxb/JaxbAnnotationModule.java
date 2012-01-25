package com.fasterxml.jackson.module.jaxb;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Module that can be registered to add support for JAXB annotations.
 * It does basically equivalent of
 *<pre>
 *   objectMapper.setAnnotationIntrospector(...);
 *</pre>
 * with combination of {@link JaxbAnnotationIntrospector} and existing
 * default introspector(s) (if any), depending on configuration
 * (by default, JAXB annotations are used as {@link Priority#PRIMARY}
 * annotations).
 */
public class JaxbAnnotationModule extends SimpleModule
{
    /**
     * Enumeration that defines how we use JAXB Annotations: either
     * as "primary" annotations (before any other already configured
     * introspector -- most likely default JacksonAnnotationIntrospector) or
     * as "secondary" annotations (after any other already configured
     * introspector(s)).
     *<p>
     * Default choice is <b>PRIMARY</b>
     *<p>
     * Note that if you want to use JAXB annotations as the only annotations,
     * you must directly set annotation introspector by calling 
     * {@link com.fasterxml.jackson.databind.ObjectMapper#setAnnotationIntrospector}.
     */
    public enum Priority {
        PRIMARY, SECONDARY;
    }
    
    /**
     * Priority to use when registering annotation introspector: default
     * value is {@link Priority#PRIMARY}.
     */
    protected Priority _priority = Priority.PRIMARY;
    
    public JaxbAnnotationModule()
    {
        super("jaxb-annotations", ModuleVersion.instance.version());
    }

    public JaxbAnnotationModule setPriority(Priority p) {
        _priority = p;
        return this;
    }

    public Priority getPriority() { return _priority; }
    
    @Override
    public void setupModule(SetupContext context)
    {
        JaxbAnnotationIntrospector intr = new JaxbAnnotationIntrospector();
        switch (_priority) {
        case PRIMARY:
            context.insertAnnotationIntrospector(intr);
            break;
        case SECONDARY:
            context.appendAnnotationIntrospector(intr);
            break;
        }
    }
}
