package com.fasterxml.jackson.module.jaxb.deser;

import java.io.IOException;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * @author Ryan Heaton
 * @author Tatu Saloranta
 */
public class XmlAdapterJsonDeserializer
    extends StdDeserializer<Object>
    implements ContextualDeserializer
{
    private static final long serialVersionUID = 1223899190001940742L;

    protected final XmlAdapter<Object,Object> _xmlAdapter;

    protected final JavaType _valueType;
    
    protected final JsonDeserializer<?> _deserializer;

    /**
     * Initial constructor, for creating instance before contextual information
     * is available
     */
    @SuppressWarnings("unchecked")
    public XmlAdapterJsonDeserializer(XmlAdapter<?,?> xmlAdapter)
    {
        this((XmlAdapter<Object,Object>) xmlAdapter, null, null);
    }

    /**
     * Constructor called during contextual resolution, when we have all the
     * pieces we actually need.
     */
    protected  XmlAdapterJsonDeserializer(XmlAdapter<Object,Object> adapter,
            JavaType valueType, JsonDeserializer<?> deserializer)
    {
        super(Object.class); // type not yet known (will be in a second), but that's ok...
        if (adapter == null) {
            throw new IllegalArgumentException("Null XmlAdapter passed");
        }
        _xmlAdapter = adapter;
        _valueType = valueType;
        _deserializer = deserializer;
    }
    
    public JsonDeserializer<Object> createContextual(DeserializationContext ctxt,
            BeanProperty property)
        throws JsonMappingException
    {
        // [JACKSON-404] Need to figure out generic type parameters used...
        TypeFactory typeFactory = ctxt.getTypeFactory();

        JavaType type = typeFactory.constructType(_xmlAdapter.getClass());
        JavaType[] rawTypes = typeFactory.findTypeParameters(type, XmlAdapter.class);
        JavaType valueType = (rawTypes == null || rawTypes.length == 0)
            ? TypeFactory.unknownType() : rawTypes[0];
        JsonDeserializer<Object> deser = ctxt.findContextualValueDeserializer(valueType, property);
        return new XmlAdapterJsonDeserializer(_xmlAdapter, valueType, deser);
    }
    
    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        /* Unfortunately we can not use the usual resolution mechanism (ResolvableDeserializer)
         * because it won't get called due to way adapters are created. So, need to do it
         * lazily when we get here:
         */
        JsonDeserializer<?> deser = _deserializer;
        if (deser == null) {
            throw new IllegalStateException("No deserializer assigned for XmlAdapterJsonDeserializer ("
                    +_xmlAdapter.getClass().getName()+"): resolve() not called?");
        }
        Object boundObject = deser.deserialize(jp, ctxt);
        try {
            return _xmlAdapter.unmarshal(boundObject);
        } catch (Exception e) {
            throw new JsonMappingException("Unable to unmarshal (to type "+_valueType+"): "+e.getMessage(), e);
        }
    }

    @Override
    public Object deserializeWithType(JsonParser jp, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer)
        throws IOException, JsonProcessingException
    {
        // Output can be as JSON Object, Array or scalar: no way to know a priori. So:
        return typeDeserializer.deserializeTypedFromAny(jp, ctxt);
    }
}
