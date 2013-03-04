package com.fasterxml.jackson.module.jaxb.ser;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonschema.SchemaAware;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class XmlAdapterJsonSerializer
    extends StdSerializer<Object>
    implements ContextualSerializer,
        SchemaAware
{
    protected final XmlAdapter<?,Object> _xmlAdapter;

    protected final boolean _fromClassAnnotation;
    
    protected final JavaType _valueType;
    
    protected final JsonSerializer<Object> _serializer;
    
    @SuppressWarnings("unchecked")
    public XmlAdapterJsonSerializer(XmlAdapter<?,?> xmlAdapter,
            boolean fromClassAnnotation) {
        this((XmlAdapter<?,Object>)  xmlAdapter, fromClassAnnotation, null, null);
    }

    protected XmlAdapterJsonSerializer(XmlAdapter<?,Object> xmlAdapter,
            boolean fromClassAnnotation,
            JavaType valueType, JsonSerializer<Object> serializer)
    {
        super(Object.class);
        _xmlAdapter = xmlAdapter;
        _fromClassAnnotation = fromClassAnnotation;
        _valueType = valueType;
        _serializer = serializer;
    }

    public JsonSerializer<Object> createContextual(SerializerProvider prov,
            BeanProperty property) throws JsonMappingException
    {
        TypeFactory typeFactory = prov.getConfig().getTypeFactory();

        JavaType type = typeFactory.constructType(_xmlAdapter.getClass());
        JavaType[] rawTypes = typeFactory.findTypeParameters(type, XmlAdapter.class);
        JavaType valueType;
        
        if (rawTypes == null || rawTypes.length < 2) {
            valueType = TypeFactory.unknownType();
        } else {
            valueType = rawTypes[0];
            /* [Issue-10]: Infinite loop for "identity" adapter; try to prevent,
             * if (but only if!) we are resolving something that came from class
             * annotation. Property annotations are ok.
             */
            if (_fromClassAnnotation) {
                JavaType otherType = rawTypes[1];
                if (otherType != null && otherType.getRawClass() == valueType.getRawClass()) {
                    // 17-Jul-2012, tatu: Report an error? Handle some other way?
                    throw new IllegalArgumentException("'Identity' adapters for Class annotations not allowed");
                }
            }
        }
        JsonSerializer<Object> ser = prov.findValueSerializer(valueType, property);
        return new XmlAdapterJsonSerializer(_xmlAdapter, _fromClassAnnotation, valueType, ser);
    }
    
    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException
    {
        Object adapted;
        // first, use adapter to get a familiar type of a value
        try {
            adapted = _xmlAdapter.marshal(value);
        } catch (IOException e) { // pass exceptions that are declared to be thrown as-is
            throw e;
        } catch (Exception e) {
            throw new JsonMappingException("Unable to marshal: "+e.getMessage(), e);
        }
        // then serialize that
        if (adapted == null) {
            // 14-Jan-2011, ideally should know property, use 'findNullValueSerializer' but...
            provider.defaultSerializeNull(jgen);
        } else {
            _checkSerializer();
            _serializer.serialize(adapted, jgen, provider);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
            throws JsonMappingException
    {
        _checkSerializer();
        if (_serializer instanceof SchemaAware) {
            return ((SchemaAware) _serializer).getSchema(provider, null);
        }
        return com.fasterxml.jackson.databind.jsonschema.JsonSchema.getDefaultSchemaNode();
    }

    private final void _checkSerializer()
    {
        if (_serializer == null) {
            throw new IllegalStateException("No serializer assigned for XmlAdapterJsonDeserializer ("
                +_xmlAdapter.getClass().getName()+"): resolve() not called?");
        }
    }
}
