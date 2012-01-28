package com.fasterxml.jackson.module.jaxb.ser;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.jsonschema.SchemaAware;
import com.fasterxml.jackson.databind.jsonschema.JsonSchema;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.*;

/**
 * @author Ryan Heaton
 */
@SuppressWarnings("restriction")
public class XmlAdapterJsonSerializer extends StdSerializer<Object>
    implements SchemaAware
{
    private final XmlAdapter<?,Object> _xmlAdapter;
    
    @SuppressWarnings("unchecked")
    public XmlAdapterJsonSerializer(XmlAdapter<?,?> xmlAdapter)
    {
        super(Object.class);
        _xmlAdapter = (XmlAdapter<?,Object>) xmlAdapter;
    }

    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException
    {
        Object adapted;
        try {
            adapted = _xmlAdapter.marshal(value);
        } catch (IOException e) { // pass exceptions that are declared to be thrown as-is
            throw e;
        } catch (Exception e) {
            throw new JsonMappingException("Unable to marshal: "+e.getMessage(), e);
        }
        if (adapted == null) {
            // 14-Jan-2011, ideally should know property, use 'findNullValueSerializer' but...
            provider.defaultSerializeNull(jgen);
        } else {
            Class<?> c = adapted.getClass();
            // true -> do cache for future lookups
            provider.findTypedValueSerializer(c, true, null).serialize(adapted, jgen, provider);
        }
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
            throws JsonMappingException
    {
        // no type resolver needed for schema
        JsonSerializer<Object> ser = provider.findValueSerializer(findValueClass(), null);
        JsonNode schemaNode = (ser instanceof SchemaAware) ?
                ((SchemaAware) ser).getSchema(provider, null) :
                JsonSchema.getDefaultSchemaNode();
        return schemaNode;
    }

    private Class<?> findValueClass()
    {
        Type superClass = this._xmlAdapter.getClass().getGenericSuperclass();
        while (superClass instanceof ParameterizedType && XmlAdapter.class != ((ParameterizedType)superClass).getRawType()) {
            superClass = ((Class<?>) ((ParameterizedType) superClass).getRawType()).getGenericSuperclass();
        }
        return (Class<?>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

}
