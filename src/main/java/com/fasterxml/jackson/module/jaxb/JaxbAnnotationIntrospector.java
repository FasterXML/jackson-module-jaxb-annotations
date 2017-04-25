package com.fasterxml.jackson.module.jaxb;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import javax.xml.bind.*;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.module.jaxb.deser.DataHandlerJsonDeserializer;
import com.fasterxml.jackson.module.jaxb.ser.DataHandlerJsonSerializer;

/**
 * Annotation introspector that leverages JAXB annotations where applicable to JSON mapping.
 * As of Jackson 2.0, most JAXB annotations are supported at least to some degree.
 * Ones that are NOT yet supported are:
 * <ul>
 * <li>{@link XmlAnyAttribute} not yet used (as of 1.5) but may be in future (as an alias for @JsonAnySetter?)
 * <li>{@link XmlAnyElement} not yet used, may be as per [JACKSON-253]
 * <li>{@link javax.xml.bind.annotation.XmlAttachmentRef}: JSON does not support external attachments
 * <li>{@link XmlElementDecl}
 * <li>{@link XmlElementRefs} because Jackson doesn't have any support for 'named' collection items -- however,
 *    this may become partially supported as per [JACKSON-253].
 * <li>{@link javax.xml.bind.annotation.XmlInlineBinaryData} since the underlying concepts
 *    (like XOP) do not exist in JSON -- Jackson will always use inline base64 encoding as the method
 * <li>{@link javax.xml.bind.annotation.XmlList} because JSON does have (or necessarily need)
 *    method of serializing list of values as space-separated Strings
 * <li>{@link javax.xml.bind.annotation.XmlMimeType}
 * <li>{@link javax.xml.bind.annotation.XmlMixed} since JSON has no concept of mixed content
 * <li>{@link XmlRegistry}
 * <li>{@link XmlSchema} not used, unlikely to be used
 * <li>{@link XmlSchemaType} not used, unlikely to be used
 * <li>{@link XmlSchemaTypes} not used, unlikely to be used
 * <li>{@link XmlSeeAlso} not yet supported, but [ISSUE-1] filed to use it, so may be supported.
 * </ul>
 *
 * Note also the following limitations:
 *
 * <ul>
 * <li>Any property annotated with {@link XmlValue} will have implicit property named 'value' on
 *    its JSON object; although (as of 2.4) it should be possible to override this name
 *   </li>
 * </ul>
 *<p>
 * A note on compatibility with Jackson XML module: since this module does not depend
 * on Jackson XML module, it is bit difficult to make sure we will properly expose
 * all information. But effort is made (as of version 2.3.3) to expose this information,
 * even without using a specific sub-class from that project.
 *
 * @author Ryan Heaton
 * @author Tatu Saloranta
 */
public class JaxbAnnotationIntrospector
    extends AnnotationIntrospector
    implements Versioned
{
    private static final long serialVersionUID = -1L;

    protected final static String DEFAULT_NAME_FOR_XML_VALUE = "value";
    
    protected final static boolean DEFAULT_IGNORE_XMLIDREF = false;
    
    protected final static String MARKER_FOR_DEFAULT = "##default";

    // @since 2.5
    protected final static JsonFormat.Value FORMAT_STRING = new JsonFormat.Value().withShape(JsonFormat.Shape.STRING);

    // @since 2.5
    protected final static JsonFormat.Value FORMAT_INT = new JsonFormat.Value().withShape(JsonFormat.Shape.NUMBER_INT);

    protected final String _jaxbPackageName;
    protected final JsonSerializer<?> _dataHandlerSerializer;
    protected final JsonDeserializer<?> _dataHandlerDeserializer;

    protected final TypeFactory _typeFactory;
    
    protected final boolean _ignoreXmlIDREF;

    /**
     * When using {@link XmlValue} annotation, a placeholder name is assigned
     * to property (unless overridden by explicit name); this configuration
     * value specified what that name is.
     */
    protected String _xmlValueName = DEFAULT_NAME_FOR_XML_VALUE;

    /**
     * Inclusion value to return for properties annotated with 
     * {@link XmlElement} and {@link XmlElementWrapper}, in case <code>nillable</code>
     * property is left as <code>false</false>. Default setting is
     * <code>null</code>; this is typically changed to either
     * {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_NULL}
     * or {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_EMPTY}.
     *
     * @since 2.7
     */
    protected JsonInclude.Include _nonNillableInclusion = null;
    
    /**
     * @deprecated Since 2.1, use constructor that takes TypeFactory.
     */
    @Deprecated
    public JaxbAnnotationIntrospector() {
        this(TypeFactory.defaultInstance());
    }

    public JaxbAnnotationIntrospector(MapperConfig<?> config) {
        this(config.getTypeFactory());
    }

    public JaxbAnnotationIntrospector(TypeFactory typeFactory) {
        this(typeFactory, DEFAULT_IGNORE_XMLIDREF);
    }

    /**
     * @param typeFactory Type factory used for resolving type information
     * @param ignoreXmlIDREF Whether {@link XmlIDREF} annotation should be processed
     *   JAXB style (meaning that references are always serialized using id), or
     *   not (first reference as full POJO, others as ids)
     */
    public JaxbAnnotationIntrospector(TypeFactory typeFactory, boolean ignoreXmlIDREF)
    {
        _typeFactory = (typeFactory == null)? TypeFactory.defaultInstance() : typeFactory;
        _ignoreXmlIDREF = ignoreXmlIDREF;
        _jaxbPackageName = XmlElement.class.getPackage().getName();

        JsonSerializer<?> dataHandlerSerializer = null;
        JsonDeserializer<?> dataHandlerDeserializer = null;
        /* Data handlers included dynamically, to try to prevent issues on platforms
         * with less than complete support for JAXB API
         */
        try {
            dataHandlerSerializer = (JsonSerializer<?>) DataHandlerJsonSerializer.class.newInstance();
            dataHandlerDeserializer = (JsonDeserializer<?>) DataHandlerJsonDeserializer.class.newInstance();
        } catch (Throwable e) {
            //dataHandlers not supported...
        }
        _dataHandlerSerializer = dataHandlerSerializer;
        _dataHandlerDeserializer = dataHandlerDeserializer;
    }

    /**
     * Method that will return version information stored in and read from jar
     * that contains this class.
     */
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Configuration method that can be used to change default name
     * ("value") used for properties annotated with {@link XmlValue};
     * note that setting it to <code>null</code> will actually avoid
     * name override, and name will instead be derived from underlying
     * method name using standard bean name introspection.
     * 
     * @since 2.5
     */
    public void setNameUsedForXmlValue(String name) {
        _xmlValueName = name;
    }

    /**
     * Accessor for getting currently configured placeholder named
     * used for property annotated with {@link XmlValue}.
     */
    public String getNameUsedForXmlValue() {
        return _xmlValueName;
    }

    /**
     * Method to call to change inclusion criteria used for property annotated
     * with {@link XmlElement} or {@link XmlElementWrapper}, with <code>nillable</code>
     * set as <code>false</code>.
     *
     * @since 2.7
     */
    public JaxbAnnotationIntrospector setNonNillableInclusion(JsonInclude.Include incl) {
        _nonNillableInclusion = incl;
        return this;
    }

    /**
     * @since 2.7
     */
    public JsonInclude.Include getNonNillableInclusion() {
        return _nonNillableInclusion;
    }

    /*
    /**********************************************************
    /* Extended API (XmlAnnotationIntrospector)
    /**********************************************************
     */

    // From XmlAnnotationIntrospector
    // @Override
    public String findNamespace(Annotated ann) {
        String ns = null;
        if (ann instanceof AnnotatedClass) {
            // For classes, it must be @XmlRootElement. Also, we do
            // want to use defaults from package, base class
            XmlRootElement elem = findRootElementAnnotation((AnnotatedClass) ann);
            if (elem != null) {
                ns = elem.namespace();
            }
        } else {
            // For others, XmlElement or XmlAttribute work (anything else?)
            XmlElement elem = findAnnotation(XmlElement.class, ann, false, false, false);
            if (elem != null) {
                ns = elem.namespace();
            }
            if (ns == null || MARKER_FOR_DEFAULT.equals(ns)) {
                XmlAttribute attr = findAnnotation(XmlAttribute.class, ann, false, false, false);
                if (attr != null) {
                    ns = attr.namespace();
                }
            }
        }
        // JAXB uses marker for "not defined"
        if (MARKER_FOR_DEFAULT.equals(ns)) {
            ns = null;
        }
        return ns;
    }

    // From XmlAnnotationIntrospector
    // @Override
    /**
     * Here we assume fairly simple logic; if there is <code>XmlAttribute</code> to be found,
     * we consider it an attribute; if <code>XmlElement</code>, not-an-attribute; and otherwise
     * we will consider there to be no information.
     * Caller is likely to default to considering things as elements.
     */
    public Boolean isOutputAsAttribute(Annotated ann) {
        XmlAttribute attr = findAnnotation(XmlAttribute.class, ann, false, false, false);
        if (attr != null) {
            return Boolean.TRUE;
        }
        XmlElement elem = findAnnotation(XmlElement.class, ann, false, false, false);
        if (elem != null) {
            return Boolean.FALSE;
        }
        return null;
    }

    // From XmlAnnotationIntrospector
    // @Override
    public Boolean isOutputAsText(Annotated ann) {
        XmlValue attr = findAnnotation(XmlValue.class, ann, false, false, false);
        if (attr != null) {
           return Boolean.TRUE;
       }
       return null;
    }
    
    /*
    /**********************************************************
    /* General annotations (for classes, properties)
    /**********************************************************
     */
    
    @Override
    public ObjectIdInfo findObjectIdInfo(Annotated ann)
    {
        /* To work in the way that works with JAXB and Jackson,
         * we need to do things in bit of round-about way, starting
         * with AnnotatedClass, locating @XmlID property, if any.
         */
        if (!(ann instanceof AnnotatedClass)) {
            return null;
        }
        AnnotatedClass ac = (AnnotatedClass) ann;
        /* Ideally, should not have to infer settings for class from
         * individual fields and/or methods; but for now this
         * has to do ...
         */
        PropertyName idPropName = null;

        method_loop:
        for (AnnotatedMethod m : ac.memberMethods()) {
            XmlID idProp = m.getAnnotation(XmlID.class);
            if (idProp == null) {
                continue;
            }
            switch (m.getParameterCount()) {
            case 0: // getter
                idPropName = findJaxbPropertyName(m, m.getRawType(),
                        BeanUtil.okNameForGetter(m, true));
                break method_loop;
            case 1: // setter
                idPropName = findJaxbPropertyName(m, m.getRawType(),
                        BeanUtil.okNameForMutator(m, "set", true));
                break method_loop;
            }
        }
        if (idPropName == null) {
            for (AnnotatedField f : ac.fields()) {
                XmlID idProp = f.getAnnotation(XmlID.class);
                if (idProp != null) {
                    idPropName = findJaxbPropertyName(f, f.getRawType(), f.getName());
                    break;
                }
            }
        }
        if (idPropName != null) {
            /* Scoping... hmmh. Could XML requires somewhat global scope, n'est pas?
             * The alternative would be to use declared type of this class.
             */
            Class<?> scope = Object.class; // alternatively would use 'ac.getRawType()'
            // and we will assume that there exists property thus named...
            return new ObjectIdInfo(idPropName,
                    scope, ObjectIdGenerators.PropertyGenerator.class,
                    // should we customize Object Id resolver somehow?
                    SimpleObjectIdResolver.class);
        }
        
        return null;
    }

    @Override
    public ObjectIdInfo findObjectReferenceInfo(Annotated ann, ObjectIdInfo base)
    {
        if (!_ignoreXmlIDREF) {
            XmlIDREF idref = ann.getAnnotation(XmlIDREF.class);
            /* JAXB makes XmlIDREF mean "always as id", as far as I know.
             * May need to make it configurable in future, but for not that
             * is fine...
             */
            if (idref != null) {
                if (base == null) {
                    base = ObjectIdInfo.empty();
                }
                base = base.withAlwaysAsId(true);
            }
        }
        return base;
    }

    /*
    /**********************************************************
    /* General class annotations
    /**********************************************************
     */

    @Override
    public PropertyName findRootName(AnnotatedClass ac)
    {
        XmlRootElement elem = findRootElementAnnotation(ac);
        if (elem != null) {
            return _combineNames(elem.name(), elem.namespace(), "");
        }
        return null;
    }
    
    /*
    @Override
    public String[] findPropertiesToIgnore(Annotated a) {
        // nothing in JAXB for this?
        return null;
    }
    */

    /* 08-Nov-2009, tatus: This is bit trickier: by default JAXB
     * does actually ignore all unknown properties.
     * But since there is no annotation to
     * specify or change this, it seems wrong to claim such setting
     * is in effect. May need to revisit this issue in future
     */
    /*
    @Override
    public Boolean findIgnoreUnknownProperties(AnnotatedClass ac);

    @Override
    public JsonIgnoreProperties.Value findPropertyIgnorals(Annotated ac);
    */

    @Override
    public Boolean isIgnorableType(AnnotatedClass ac) {
        // Does JAXB have any such indicators? No?
        return null;
    }

    /*
    /**********************************************************
    /* General member (field, method/constructor) annotations
    /**********************************************************
     */

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        return m.getAnnotation(XmlTransient.class) != null;
    }

    //(ryan) JAXB has @XmlAnyAttribute and @XmlAnyElement annotations, but they're not applicable in this case
    // because JAXB says those annotations are only applicable to methods with specific signatures
    // that Jackson doesn't support (Jackson's any setter needs 2 arguments, name and value, whereas
    // JAXB expects use of Map

    // 28-May-2016, tatu: While `@XmlAnyAttribute` looks ALMOST like applicable (esp.
    //   assuming Jackson could use `Map` field, not just setter/getter), it is alas not.
    //   The reason is that key is expected to be `QNmae`, XML/JAXB specific name and
    //   something Jackson does not require or use
    
    /*
    @Override
    public boolean hasAnySetterAnnotation(AnnotatedMethod am) { }
    
    @Override
    public boolean hasAnySetterAnnotation(AnnotatedMethod am)
    */

    @Override
    public Boolean hasRequiredMarker(AnnotatedMember m) {
        XmlElement elem = m.getAnnotation(XmlElement.class);
        if ((elem != null) && elem.required()) {
            return Boolean.TRUE;
        }
        XmlAttribute attr = m.getAnnotation(XmlAttribute.class);
        if ((attr != null) && attr.required()) {
            return Boolean.TRUE;
        }
        // 09-Sep-2015, tatu: Not 100% sure that we should ever return `false`
        //   here (as it blocks calls to secondary introspector), but since that
        //   was the existing behavior before 2.6, is retained for now.
        if ((elem != null) || (attr != null)) {
            return null;
        }
        return Boolean.FALSE;
    }

    @Override
    public PropertyName findWrapperName(Annotated ann)
    {
        XmlElementWrapper w = findAnnotation(XmlElementWrapper.class, ann, false, false, false);
        if (w != null) {
            /* 18-Sep-2013, tatu: As per [jaxb-annotations#24], need to take special care with empty
             *   String, as that should indicate here "use underlying unmodified
             *   property name" (that is, one NOT overridden by @JsonProperty)
             */
            PropertyName name =  _combineNames(w.name(), w.namespace(), "");
            // clumsy, yes, but has to do:
            if (!name.hasSimpleName()) {
                if (ann instanceof AnnotatedMethod) {
                    AnnotatedMethod am = (AnnotatedMethod) ann;
                    String str;
                    if (am.getParameterCount() == 0) {
                        str = BeanUtil.okNameForGetter(am, true);
                    } else {
                        str = BeanUtil.okNameForMutator(am, "set", true);
                    }
                    if (str != null) {
                        return name.withSimpleName(str);
                    }
                }
                return name.withSimpleName(ann.getName());
            }
            return name;
        }
        return null;
    }

    // since 2.4
    @Override
    public String findImplicitPropertyName(AnnotatedMember m) {
        XmlValue valueInfo = m.getAnnotation(XmlValue.class);
        if (valueInfo != null) {
            return _xmlValueName;
        }
        return null;
    }

    @Override
    public JsonFormat.Value findFormat(Annotated m) {
        /* [jaxb-annotations#33]: Use @XmlEnum value (Class) to indicate format,
         *   iff it makes sense
         */
        if (m instanceof AnnotatedClass) {
            XmlEnum ann = m.getAnnotation(XmlEnum.class);
            if (ann != null) {
                Class<?> type = ann.value();
                if (type == String.class || type.isEnum()) {
                    return FORMAT_STRING;
                }
                if (Number.class.isAssignableFrom(type)) {
                    return FORMAT_INT;
                }
            }
        }
        return null;
    }
    
    /*
    /**********************************************************
    /* Property auto-detection
    /**********************************************************
     */
    
    @Override
    public VisibilityChecker<?> findAutoDetectVisibility(AnnotatedClass ac,
        VisibilityChecker<?> checker)
    {
        XmlAccessType at = findAccessType(ac);
        if (at == null) {
            /* JAXB default is "PUBLIC_MEMBER"; however, here we should not
             * override settings if there is no annotation -- that would mess
             * up global baseline. Fortunately Jackson defaults are very close
             * to JAXB 'PUBLIC_MEMBER' settings (considering that setters and
             * getters must come in pairs)
             */
            return checker;
        }
        
        // Note: JAXB does not do creator auto-detection, can (and should) ignore
        switch (at) {
        case FIELD: // all fields, independent of visibility; no methods
            return checker.withFieldVisibility(Visibility.ANY)
                .withSetterVisibility(Visibility.NONE)
                .withGetterVisibility(Visibility.NONE)
                .withIsGetterVisibility(Visibility.NONE)
                ;
        case NONE: // no auto-detection
            return checker.withFieldVisibility(Visibility.NONE)
            .withSetterVisibility(Visibility.NONE)
            .withGetterVisibility(Visibility.NONE)
            .withIsGetterVisibility(Visibility.NONE)
            ;
        case PROPERTY:
            return checker.withFieldVisibility(Visibility.NONE)
            .withSetterVisibility(Visibility.PUBLIC_ONLY)
            .withGetterVisibility(Visibility.PUBLIC_ONLY)
            .withIsGetterVisibility(Visibility.PUBLIC_ONLY)
            ;
        case PUBLIC_MEMBER:       
            return checker.withFieldVisibility(Visibility.PUBLIC_ONLY)
            .withSetterVisibility(Visibility.PUBLIC_ONLY)
            .withGetterVisibility(Visibility.PUBLIC_ONLY)
            .withIsGetterVisibility(Visibility.PUBLIC_ONLY)
            ;
        }
        return checker;
    }

    /**
     * Method for locating JAXB {@link XmlAccessType} annotation value
     * for given annotated entity, if it has one, or inherits one from
     * its ancestors (in JAXB sense, package etc). Returns null if
     * nothing has been explicitly defined.
     */
    protected XmlAccessType findAccessType(Annotated ac)
    {
        XmlAccessorType at = findAnnotation(XmlAccessorType.class, ac, true, true, true);
        return (at == null) ? null : at.value();
    }
    
    /*
    /**********************************************************
    /* Class annotations for PM type handling (1.5+)
    /**********************************************************
     */
    
    @Override
    public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config,
            AnnotatedClass ac, JavaType baseType)
    {
        // no per-class type resolvers, right?
        return null;
    }

    @Override
    public TypeResolverBuilder<?> findPropertyTypeResolver(MapperConfig<?> config,
            AnnotatedMember am, JavaType baseType)
    {
        /* First: @XmlElements and @XmlElementRefs only applies type for immediate property, if it
         * is NOT a structured type.
         */
        if (baseType.isContainerType()) return null;
        return _typeResolverFromXmlElements(am);
    }

    @Override
    public TypeResolverBuilder<?> findPropertyContentTypeResolver(MapperConfig<?> config,
            AnnotatedMember am, JavaType containerType)
    {
        /* First: let's ensure property is a container type: caller should have
         * verified but just to be sure
         */
        if (containerType.getContentType() == null) {
            throw new IllegalArgumentException("Must call method with a container or reference type (got "+containerType+")");
        }
        return _typeResolverFromXmlElements(am);
    }

    protected TypeResolverBuilder<?> _typeResolverFromXmlElements(AnnotatedMember am)
    {
        /* If simple type, @XmlElements and @XmlElementRefs are applicable.
         * Note: @XmlElement and @XmlElementRef are NOT handled here, since they
         * are handled specifically as non-polymorphic indication
         * of the actual type
         */
        XmlElements elems = findAnnotation(XmlElements.class, am, false, false, false);
        XmlElementRefs elemRefs = findAnnotation(XmlElementRefs.class, am, false, false, false);
        if (elems == null && elemRefs == null) {
            return null;
        }

        TypeResolverBuilder<?> b = new StdTypeResolverBuilder();
        // JAXB always uses type name as id
        b = b.init(JsonTypeInfo.Id.NAME, null);
        // and let's consider WRAPPER_OBJECT to be canonical inclusion method
        b = b.inclusion(JsonTypeInfo.As.WRAPPER_OBJECT);
        return b;        
    }
    
    @Override
    public List<NamedType> findSubtypes(Annotated a)
    {
        // No package/superclass defaulting (only used with fields, methods)
        XmlElements elems = findAnnotation(XmlElements.class, a, false, false, false);
        ArrayList<NamedType> result = null;
        if (elems != null) {
            result = new ArrayList<NamedType>();
            for (XmlElement elem : elems.value()) {
                String name = elem.name();
                if (MARKER_FOR_DEFAULT.equals(name)) name = null;
                result.add(new NamedType(elem.type(), name));
            }
        } else {
            XmlElementRefs elemRefs = findAnnotation(XmlElementRefs.class, a, false, false, false);
            if (elemRefs != null) {
                result = new ArrayList<NamedType>();
                for (XmlElementRef elemRef : elemRefs.value()) {
                    Class<?> refType = elemRef.type();
                    // only good for types other than JAXBElement (which is XML based)
                    if (!JAXBElement.class.isAssignableFrom(refType)) {
                        // first consider explicit name declaration
                        String name = elemRef.name();
                        if (name == null || MARKER_FOR_DEFAULT.equals(name)) {
                            XmlRootElement rootElement = (XmlRootElement) refType.getAnnotation(XmlRootElement.class);
                            if (rootElement != null) {
                                name = rootElement.name();
                            }
                        }
                        if (name == null || MARKER_FOR_DEFAULT.equals(name)) {
                            name = Introspector.decapitalize(refType.getSimpleName());
                        }
                        result.add(new NamedType(refType, name));
                    }
                }
            }
        }
        
        // Check @XmlSeeAlso as well.
        /* 17-Aug-2012, tatu:  But wait! For structured type, what we really is
         *    value (content) type!
         *    If code below does not make full (or any) sense, do not despair -- it
         *    is wrong. Yet it works. The call sequence before we get here is mangled,
         *    its logic twisted... but as Dire Straits put it: "That ain't working --
         *    that's The Way You Do It!"
         */
        XmlSeeAlso ann = a.getAnnotation(XmlSeeAlso.class);
        if (ann != null) {
            if (result == null) {
                result = new ArrayList<NamedType>();
            }
            for (Class<?> cls : ann.value()) {
                result.add(new NamedType(cls));
            }
        }
        return result;
    }

    @Override
    public String findTypeName(AnnotatedClass ac) {
        XmlType type = findAnnotation(XmlType.class, ac, false, false, false);
        if (type != null) {
            String name = type.name();
            if (!MARKER_FOR_DEFAULT.equals(name)) return name;
        }
        return null;
    }

    /*
    /**********************************************************
    /* Serialization: general annotations
    /**********************************************************
     */

    @Override
    public JsonSerializer<?> findSerializer(Annotated am)
    {
        final Class<?> type = _rawSerializationType(am);

        /*
        // As per [JACKSON-722], more checks for structured types
        XmlAdapter<Object,Object> adapter = findAdapter(am, true, type);
        if (adapter != null) {
            boolean fromClass = !(am instanceof AnnotatedMember);
            // Ugh. Must match to see if adapter's bounded type (result to map to) matches property type
            if (isContainerType(type)) {
                Class<?> bt = findAdapterBoundType(adapter);
                if (bt.isAssignableFrom(type)) {
                    return new XmlAdapterJsonSerializer(adapter, fromClass);
                }
                // Note: if used for value type, handled in different place
            } else {
                return new XmlAdapterJsonSerializer(adapter, fromClass);
            }
        }
        */

        // Add support for additional core XML types needed by JAXB
        if (type != null) {
            if (_dataHandlerSerializer != null && isDataHandler(type)) {
                return _dataHandlerSerializer;
            }
        }
        return null;
    }

    /**
     * Determines whether the type is assignable to class javax.activation.DataHandler without requiring that class
     * to be on the classpath.
     *
     * @param type The type.
     * @return Whether the type is assignable to class javax.activation.DataHandler
     */
    private boolean isDataHandler(Class<?> type)
    {
        return type != null && (Object.class != type)
               && (("javax.activation.DataHandler".equals(type.getName()) || isDataHandler(type.getSuperclass())));
    }

    @Override
    public Object findContentSerializer(Annotated a) {
        return null;
    }

    @Override
    @Deprecated // since 2.7
    public Class<?> findSerializationType(Annotated a)
    {
        Class<?> allegedType = _getTypeFromXmlElement(a);
        if (allegedType != null){
            Class<?> rawPropType = _rawSerializationType(a);
            if (!isContainerType(rawPropType)) {
                return allegedType;
            }
        }
        return null;
    }

    @Override // @since 2.7
    public JsonInclude.Value findPropertyInclusion(Annotated a)
    {
        JsonInclude.Include incl = _serializationInclusion(a, null);
        if (incl == null) {
            return JsonInclude.Value.empty();
        }
        return JsonInclude.Value.construct(incl, null);
    }

    JsonInclude.Include _serializationInclusion(Annotated a, JsonInclude.Include defValue)
    {
        XmlElementWrapper w = a.getAnnotation(XmlElementWrapper.class);
        if (w != null) {
            if (w.nillable()) {
                return JsonInclude.Include.ALWAYS;
            }
            // [jaxb-annotations#52]: Allow specifying inclusion for `nillable=false` too
            if (_nonNillableInclusion != null) {
                return _nonNillableInclusion;
            }
        }
        XmlElement e = a.getAnnotation(XmlElement.class);
        if (e != null) {
            if (e.nillable()) {
                return JsonInclude.Include.ALWAYS;
            }
            // [jaxb-annotations#52]: Allow specifying inclusion for `nillable=false` too
            if (_nonNillableInclusion != null) {
                return _nonNillableInclusion;
            }
        }
        //better pass default value through, if no explicit direction indicating otherwise
        return defValue;
    }

    @Override // @since 2.7
    public JavaType refineSerializationType(final MapperConfig<?> config,
            final Annotated a, final JavaType baseType) throws JsonMappingException
    {
        Class<?> serClass = _getTypeFromXmlElement(a);
        if (serClass == null) {
            return baseType;
        }
        // First, JAXB has no annotations for key type, so can skip that part (wrt std annotations)
        // But the meaning of main annotation(s) varies between container/non-container types
        final TypeFactory tf = config.getTypeFactory();
        if (baseType.getContentType() == null) { // non-container/-structured types, usually scalar:
            // 27-Nov-2015, tatu: Since JAXB has just one annotation, must ignore it in
            //   one direction, typically serialization (but not always):
            if (!serClass.isAssignableFrom(baseType.getRawClass())) {
                return baseType;
            }
            if (baseType.hasRawClass(serClass)) {
                // 30-Nov-2015, tatu: As per [databind#1023], need to allow forcing of
                //    static typing this way
                return baseType.withStaticTyping();
            }
            try {
                return tf.constructGeneralizedType(baseType, serClass);
            } catch (IllegalArgumentException iae) {
                throw new JsonMappingException(null,
                        String.format("Failed to widen type %s with annotation (value %s), from '%s': %s",
                                baseType, serClass.getName(), a.getName(), iae.getMessage()),
                                iae);
            }
        } else {
            // Otherwise, structured type:
            JavaType contentType = baseType.getContentType();
            if (contentType != null) { // collection[like], map[like], array, reference
                // as per earlier, may need to ignore annotation meant for deserialization
                if (!serClass.isAssignableFrom(contentType.getRawClass())) {
                    return baseType;
                }
                // And then value types for all containers:
                if (contentType.hasRawClass(serClass)) {
                     contentType = contentType.withStaticTyping();
                } else {
                    try {
                       contentType = tf.constructGeneralizedType(contentType, serClass);
                    } catch (IllegalArgumentException iae) {
                        throw new JsonMappingException(null,
                                String.format("Failed to widen value type of %s with concrete-type annotation (value %s), from '%s': %s",
                                        baseType, serClass.getName(), a.getName(), iae.getMessage()),
                                        iae);
                    }
                }
                return baseType.withContentType(contentType);
            }
        }
        return baseType;
    }

    /*
    /**********************************************************
    /* Serialization: class annotations
    /**********************************************************
     */

    @Override
    public String[] findSerializationPropertyOrder(AnnotatedClass ac)
    {
        // @XmlType.propOrder fits the bill here:
        XmlType type = findAnnotation(XmlType.class, ac, true, true, true);
        if (type == null) {
            return null;
        }
        String[] order = type.propOrder();
        if (order == null || order.length == 0) {
            return null;
        }
        return order;
    }

    @Override
    public Boolean findSerializationSortAlphabetically(Annotated ann) {
        return _findAlpha(ann);
    }

    private final Boolean _findAlpha(Annotated ann) {
        XmlAccessorOrder order = findAnnotation(XmlAccessorOrder.class, ann, true, true, true);
        return (order == null) ? null : (order.value() == XmlAccessOrder.ALPHABETICAL);
    }
    
    @Override
    public Object findSerializationConverter(Annotated a)
    {
        Class<?> serType = _rawSerializationType(a);
        // Can apply to both container and regular type; no difference yet here
        XmlAdapter<?,?> adapter = findAdapter(a, true, serType);
        if (adapter != null) {
            return _converter(adapter, true);
        }
        return null;
    }

    @Override
    public Object findSerializationContentConverter(AnnotatedMember a)
    {
        // But this one only applies to structured (container) types:
        Class<?> serType = _rawSerializationType(a);
        if (isContainerType(serType)) {
            XmlAdapter<?,?> adapter = _findContentAdapter(a, true);
            if (adapter != null) {
                return _converter(adapter, true);
            }
        }
        return null;
    }

    /*
    /**********************************************************
    /* Serialization: property annotations
    /**********************************************************
     */

    @Override
    public PropertyName findNameForSerialization(Annotated a)
    {
        // 16-Sep-2016, tatu: Prior to 2.9 logic her more complicated, on assumption
        //    that visibility rules may require return of "" if method/fied visible;
        //    however, that is not required and causes issues so... now simpler:
        if (a instanceof AnnotatedMethod) {
            AnnotatedMethod am = (AnnotatedMethod) a;
            return isVisible(am)
                ? findJaxbPropertyName(am, am.getRawType(), BeanUtil.okNameForGetter(am, true))
                : null;
        }
        if (a instanceof AnnotatedField) {
            AnnotatedField af = (AnnotatedField) a;
            return isVisible(af)
                ? findJaxbPropertyName(af, af.getRawType(), null)
                : null;
        }
        return null;
    }

    @Deprecated // since 2.9
    @Override
    public boolean hasAsValueAnnotation(AnnotatedMethod am) {
        //since jaxb says @XmlValue can exist with attributes, this won't map as a JSON value.
        return false;
    }

    // As per above, nothing to detect here either...?
    /*
    @Override
    public Boolean findAsValueAnnotation(Annotated a) {
    }
    */

    @Override // since 2.7
    public String[] findEnumValues(Class<?> enumType, Enum<?>[] enumValues, String[] names) {
        HashMap<String,String> expl = null;
        for (Field f : ClassUtil.getDeclaredFields(enumType)) {
            if (!f.isEnumConstant()) {
                continue;
            }
            XmlEnumValue enumValue = f.getAnnotation(XmlEnumValue.class);
            if (enumValue == null) {
                continue;
            }
            String n = enumValue.value();
            if (n.isEmpty()) {
                continue;
            }
            if (expl == null) {
                expl = new HashMap<String,String>();
            }
            expl.put(f.getName(), n);
        }
        // and then stitch them together if and as necessary
        if (expl != null) {
            for (int i = 0, end = enumValues.length; i < end; ++i) {
                String defName = enumValues[i].name();
                String explValue = expl.get(defName);
                if (explValue != null) {
                    names[i] = explValue;
                }
            }
        }
        return names;
    }

    /*
    /**********************************************************
    /* Deserialization: general annotations
    /**********************************************************
     */
    
    @Override
    public Object findDeserializer(Annotated am)
    {
        final Class<?> type = _rawDeserializationType(am);

        /*
        // As per [JACKSON-722], more checks for structured types
        XmlAdapter<Object,Object> adapter = findAdapter(am, true, type);
        if (adapter != null) {
            // Ugh. Must match to see if adapter's bounded type (result to map to) matches property type
            if (isContainerType(type)) {
                if (adapterTypeMatches(adapter, type)) {
                    return new XmlAdapterJsonDeserializer(adapter);
                }
            } else {
                return new XmlAdapterJsonDeserializer(adapter);
            }
        }
        */

        // [JACKSON-150]: add support for additional core XML types needed by JAXB
        if (type != null) {
            if (_dataHandlerDeserializer != null && isDataHandler(type)) {
                return _dataHandlerDeserializer;
            }
        }

        return null;
    }

    @Override
    public Object findKeyDeserializer(Annotated am) {
        // Is there something like this in JAXB?
        return null;
    }

    @Override
    public Object findContentDeserializer(Annotated a) {
        return null;
    }

    protected Class<?> _doFindDeserializationType(Annotated a, JavaType baseType)
    {
        /* @XmlJavaTypeAdapter will complicate handling of type information;
         * basically we better just ignore type we might find here altogether in that case
         */
        if (a.hasAnnotation(XmlJavaTypeAdapter.class)) {
            return null;
        }
        
        // false for class, package, super-class, since annotation can
        // only be attached to fields and methods
        XmlElement annotation = findAnnotation(XmlElement.class, a, false, false, false);
        if (annotation != null) {
            Class<?> type = annotation.type();
            if (type != XmlElement.DEFAULT.class) {
                return type;
            }
        }
        return null;
    }

    // @since 2.7
    @Override
    public JavaType refineDeserializationType(final MapperConfig<?> config,
            final Annotated a, final JavaType baseType) throws JsonMappingException
    {
        Class<?> deserClass = _getTypeFromXmlElement(a);
        if (deserClass == null) {
            return baseType;
        }

        final TypeFactory tf = config.getTypeFactory();

        if (baseType.getContentType() == null) { // non-container/-structured types, usually scalar:
            if (baseType.hasRawClass(deserClass)) { // no change
                return baseType;
            }
            // 27-Nov-2015, tatu: Since JAXB has just one annotation, must ignore it in
            //   one direction, typically serialization (but not always):
            if (!baseType.getRawClass().isAssignableFrom(deserClass)) {
                return baseType;
            }
            try {
                return tf.constructSpecializedType(baseType, deserClass);
            } catch (IllegalArgumentException iae) {
                throw new JsonMappingException(null,
                        String.format("Failed to narrow type %s with annotation (value %s), from '%s': %s",
                                baseType, deserClass.getName(), a.getName(), iae.getMessage()),
                                iae);
            }
        } else {
            // Otherwise, structured type:
            JavaType contentType = baseType.getContentType();
            if (contentType != null) { // collection[like], map[like], array, reference
                // as per earlier, may need to ignore annotation meant for deserialization
                if (!contentType.getRawClass().isAssignableFrom(deserClass)) {
                    return baseType;
                }
                // And then value types for all containers:
                try {
                   contentType = tf.constructSpecializedType(contentType, deserClass);
                   return baseType.withContentType(contentType);
                } catch (IllegalArgumentException iae) {
                    throw new JsonMappingException(null,
                            String.format("Failed to narrow type %s with annotation (value %s), from '%s': %s",
                                    baseType, deserClass.getName(), a.getName(), iae.getMessage()),
                                    iae);
                }
            }
        }
        return baseType;
    }

    /*
    /**********************************************************
    /* Deserialization: property annotations
    /**********************************************************
     */

    @Override
    public PropertyName findNameForDeserialization(Annotated a)
    {
        // 16-Sep-2016, tatu: Prior to 2.9 logic her more complicated, on assumption
        //    that visibility rules may require return of "" if method/fied visible;
        //    however, that is not required and causes issues so... now simpler:
        if (a instanceof AnnotatedMethod) {
            AnnotatedMethod am = (AnnotatedMethod) a;
            if (!isVisible(am)) {
                return null;
            }
            Class<?> rawType = am.getRawParameterType(0);
            return findJaxbPropertyName(am, rawType, BeanUtil.okNameForMutator(am, "set", true));
        }
        if (a instanceof AnnotatedField) {
            AnnotatedField af = (AnnotatedField) a;
            return isVisible(af)
                ? findJaxbPropertyName(af, af.getRawType(), null)
                : null;
        }
        return null;
    }

    @Override
    public Object findDeserializationConverter(Annotated a)
    {
        // One limitation: for structured types this is done later on
        Class<?> deserType = _rawDeserializationType(a);
        if (isContainerType(deserType)) {
            XmlAdapter<?,?> adapter = findAdapter(a, true, deserType);
            if (adapter != null) {
                return _converter(adapter, false);
            }
        } else {
            XmlAdapter<?,?> adapter = findAdapter(a, true, deserType);
            if (adapter != null) {
                return _converter(adapter, false);
            }
        }
        return null;
    }

    @Override
    public Object findDeserializationContentConverter(AnnotatedMember a)
    {
        // conversely, here we only apply this to container types:
        Class<?> deserType = _rawDeserializationType(a);
        if (isContainerType(deserType)) {
            XmlAdapter<?,?> adapter = _findContentAdapter(a, false);
            if (adapter != null) {
                return _converter(adapter, false);
            }
        }
        return null;
    }    
    /*
    /**********************************************************
    /* Helper methods (non-API)
    /**********************************************************
     */

    /**
     * Whether the specified field is invisible, per the JAXB visibility rules.
     *
     * @param f The field.
     * @return Whether the field is invisible.
     */
    private boolean isVisible(AnnotatedField f)
    {
        // TODO: use AnnotatedField's annotations directly
        for (Annotation annotation : f.getAnnotated().getDeclaredAnnotations()) {
            if (isJAXBAnnotation(annotation)) {
                return true;
            }
        }
        XmlAccessType accessType = XmlAccessType.PUBLIC_MEMBER;
        XmlAccessorType at = findAnnotation(XmlAccessorType.class, f, true, true, true);
        if (at != null) {
            accessType = at.value();
        }
        if (accessType == XmlAccessType.FIELD) {
            return true;
        }
        if (accessType == XmlAccessType.PUBLIC_MEMBER) {
            return Modifier.isPublic(f.getAnnotated().getModifiers());
        }
        return false;
    }

    private boolean isVisible(AnnotatedMethod m)
    {
        // TODO: use AnnotatedField's annotations directly
        for (Annotation annotation : m.getAnnotated().getDeclaredAnnotations()) {
            if (isJAXBAnnotation(annotation)) {
                return true;
            }
        }
        XmlAccessType accessType = XmlAccessType.PUBLIC_MEMBER;
        XmlAccessorType at = findAnnotation(XmlAccessorType.class, m, true, true, true);
        if (at != null) {
            accessType = at.value();
        }
        if (accessType == XmlAccessType.PROPERTY || accessType == XmlAccessType.PUBLIC_MEMBER) {
            return Modifier.isPublic(m.getModifiers());
        }
        return false;
    }
    
    /**
     * Finds an annotation associated with given annotatable thing; or if
     * not found, a default annotation it may have (from super class, package
     * and so on)
     *
     * @param annotationClass the annotation class.
     * @param annotated The annotated element.
     * @param includePackage Whether the annotation can be found on the package of the annotated element.
     * @param includeClass Whether the annotation can be found on the class of the annotated element.
     * @param includeSuperclasses Whether the annotation can be found on any superclasses of the class of the annotated element.
     * @return The annotation, or null if not found.
     */
    private <A extends Annotation> A findAnnotation(Class<A> annotationClass, Annotated annotated,
            boolean includePackage, boolean includeClass, boolean includeSuperclasses)
    {
        A annotation = annotated.getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }
        Class<?> memberClass = null;
        /* 13-Feb-2011, tatu: [JACKSON-495] - need to handle AnnotatedParameter
         *   bit differently, since there is no JDK counterpart. We can still
         *   access annotations directly, just using different calls.
         */
        if (annotated instanceof AnnotatedParameter) {
            memberClass = ((AnnotatedParameter) annotated).getDeclaringClass();
        } else {
            AnnotatedElement annType = annotated.getAnnotated();
            if (annType instanceof Member) {
                memberClass = ((Member) annType).getDeclaringClass();
                if (includeClass) {
                    annotation = (A) memberClass.getAnnotation(annotationClass);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            } else if (annType instanceof Class<?>) {
                memberClass = (Class<?>) annType;
            } else {
                throw new IllegalStateException("Unsupported annotated member: " + annotated.getClass().getName());
            }
        }
        if (memberClass != null) {
            if (includeSuperclasses) {
                Class<?> superclass = memberClass.getSuperclass();
                while (superclass != null && superclass != Object.class) {
                    annotation = (A) superclass.getAnnotation(annotationClass);
                    if (annotation != null) {
                        return annotation;
                    }
                    superclass = superclass.getSuperclass();
                }
            }
            if (includePackage) {
                Package pkg = memberClass.getPackage();
                if (pkg != null) {
                    return memberClass.getPackage().getAnnotation(annotationClass);
                }
            }
        }
        return null;
    }

    /*
    /**********************************************************
    /* Helper methods for bean property introspection
    /**********************************************************
     */

    /**
     * An annotation is handled if it's in the same package as @XmlElement, including subpackages.
     *
     * @param ann The annotation.
     * @return Whether the annotation is in the JAXB package.
     */
    protected boolean isJAXBAnnotation(Annotation ann)
    {
        /* note: class we want is the annotation class, not instance
         * (since annotation instances, like enums, may be of different
         * physical type!)
         */
        Class<?> cls = ann.annotationType();
        Package pkg = cls.getPackage();
        String pkgName = (pkg != null) ? pkg.getName() : cls.getName();
        if (pkgName.startsWith(_jaxbPackageName)) {
            return true;
        }
        return false;
    }
    
    private PropertyName findJaxbPropertyName(Annotated ae, Class<?> aeType, String defaultName)
    {
        XmlAttribute attribute = ae.getAnnotation(XmlAttribute.class);
        if (attribute != null) {
            return _combineNames(attribute.name(), attribute.namespace(), defaultName);
        }
        XmlElement element = ae.getAnnotation(XmlElement.class);
        if (element != null) {
            return _combineNames(element.name(), element.namespace(), defaultName);
        }
        XmlElementRef elementRef = ae.getAnnotation(XmlElementRef.class);
        boolean hasAName = (elementRef != null);
        if (hasAName) {
            if (!MARKER_FOR_DEFAULT.equals(elementRef.name())) {
                return _combineNames(elementRef.name(), elementRef.namespace(), defaultName);
            }
            if (aeType != null) {
                XmlRootElement rootElement = (XmlRootElement) aeType.getAnnotation(XmlRootElement.class);
                if (rootElement != null) {
                    String name = rootElement.name();
                    if (!MARKER_FOR_DEFAULT.equals(name)) {
                        return _combineNames(name, rootElement.namespace(), defaultName);
                    }
                    // Is there a namespace there to use? Probably not?
                    return new PropertyName(Introspector.decapitalize(aeType.getSimpleName()));
                }
            }
        }
        if (!hasAName) {
            hasAName = ae.hasAnnotation(XmlElementWrapper.class)
                    // 09-Aug-2014, tatu: Note: prior to 2.4.2, we used to give explicit name "value"
                    //   if there was "@XmlValue" annotation; since then, only implicit name.
                    || ae.hasAnnotation(XmlValue.class);
        }
        // One more thing: 
        return hasAName ? PropertyName.USE_DEFAULT : null;
    }

    private static PropertyName _combineNames(String localName, String namespace,
            String defaultName)
    {
        if (MARKER_FOR_DEFAULT.equals(localName)) {
            if (MARKER_FOR_DEFAULT.equals(namespace)) {
                return new PropertyName(defaultName);
            }
            return new PropertyName(defaultName, namespace);
        }
        if (MARKER_FOR_DEFAULT.equals(namespace)) {
            return new PropertyName(localName);
        }
        return new PropertyName(localName, namespace);
    }
    
    private XmlRootElement findRootElementAnnotation(AnnotatedClass ac)
    {
        // Yes, check package, no class (already included), yes superclasses
        return findAnnotation(XmlRootElement.class, ac, true, false, true);
    }

    /**
     * Finds the XmlAdapter for the specified annotation.
     *
     * @param am The annotated element.
     * @param forSerialization If true, adapter for serialization; if false, for deserialization
     * @param type
     * 
     * @return The adapter, or null if none.
     */
    private XmlAdapter<Object,Object> findAdapter(Annotated am, boolean forSerialization,
            Class<?> type)
    {
        // First of all, are we looking for annotations for class?
        if (am instanceof AnnotatedClass) {
            return findAdapterForClass((AnnotatedClass) am, forSerialization);
        }
        // Otherwise for a member. First, let's figure out type of property
        XmlJavaTypeAdapter adapterInfo = findAnnotation(XmlJavaTypeAdapter.class, am, true, false, false);
        if (adapterInfo != null) {
            XmlAdapter<Object,Object> adapter = checkAdapter(adapterInfo, type, forSerialization);
            if (adapter != null) {
                return adapter;
            }
        }
        XmlJavaTypeAdapters adapters = findAnnotation(XmlJavaTypeAdapters.class, am, true, false, false);
        if (adapters != null) {
            for (XmlJavaTypeAdapter info : adapters.value()) {
                XmlAdapter<Object,Object> adapter = checkAdapter(info, type, forSerialization);
                if (adapter != null) {
                    return adapter;
                }
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private final XmlAdapter<Object,Object> checkAdapter(XmlJavaTypeAdapter adapterInfo, Class<?> typeNeeded,
            boolean forSerialization)
    {
        // if annotation has no type, it's applicable; if it has, must match
        Class<?> adaptedType = adapterInfo.type();
        
        if (adaptedType == XmlJavaTypeAdapter.DEFAULT.class) {
            JavaType type = _typeFactory.constructType(adapterInfo.value());
            JavaType[] params = _typeFactory.findTypeParameters(type, XmlAdapter.class);
            adaptedType = params[1].getRawClass();
        }
        if (adaptedType.isAssignableFrom(typeNeeded)) {
            @SuppressWarnings("rawtypes")
            Class<? extends XmlAdapter> cls = adapterInfo.value();
            // true -> yes, force access if need be
            return ClassUtil.createInstance(cls, true);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private XmlAdapter<Object,Object> findAdapterForClass(AnnotatedClass ac, boolean forSerialization)
    {
        /* As per [JACKSON-411], XmlJavaTypeAdapter should not be inherited from super-class.
         * It would still be nice to be able to use mix-ins; but unfortunately we seem to lose
         * knowledge of class that actually declared the annotation. Thus, we'll only accept
         * declaration from specific class itself.
         */
        XmlJavaTypeAdapter adapterInfo = ac.getAnnotated().getAnnotation(XmlJavaTypeAdapter.class);
        if (adapterInfo != null) { // should we try caching this?
            @SuppressWarnings("rawtypes")
            Class<? extends XmlAdapter> cls = adapterInfo.value();
            // true -> yes, force access if need be
            return ClassUtil.createInstance(cls, true);
        }
        return null;
    }

    protected final TypeFactory getTypeFactory() {
        return _typeFactory;
    }
    
    /**
     * Helper method used to distinguish structured types (arrays, Lists, Maps),
     * which with JAXB use different rules for defining content types.
     */
    private boolean isContainerType(Class<?> raw)
    {
        return raw.isArray() || Collection.class.isAssignableFrom(raw)
            || Map.class.isAssignableFrom(raw);
    }

    private boolean adapterTypeMatches(XmlAdapter<?,?> adapter, Class<?> targetType)
    {
        return findAdapterBoundType(adapter).isAssignableFrom(targetType);
    }

    private Class<?> findAdapterBoundType(XmlAdapter<?,?> adapter)
    {
        TypeFactory tf = getTypeFactory();
        JavaType adapterType = tf.constructType(adapter.getClass());
        JavaType[] params = tf.findTypeParameters(adapterType, XmlAdapter.class);
        // should not happen, except if our type resolution has a flaw, but:
        if (params == null || params.length < 2) {
            return Object.class;
        }
        return params[1].getRawClass();
    }

    protected XmlAdapter<?,?> _findContentAdapter(Annotated ann,
            boolean forSerialization)
    {
        Class<?> rawType = forSerialization ?
            _rawSerializationType(ann) : _rawDeserializationType(ann);
        
        // and let's assume this only applies as member annotation:
        if (isContainerType(rawType) && (ann instanceof AnnotatedMember)) {
            AnnotatedMember member = (AnnotatedMember) ann;
            JavaType fullType = forSerialization ?
                _fullSerializationType(member) : _fullDeserializationType(member);
            Class<?> contentType = fullType.getContentType().getRawClass();
            XmlAdapter<Object,Object> adapter = findAdapter(member, forSerialization, contentType);
            if (adapter != null && adapterTypeMatches(adapter, contentType)) {
                return adapter;
            }
        }
        return null;
    }
    
    protected String _propertyNameToString(PropertyName n)
    {
        return (n == null) ? null : n.getSimpleName();
    }

    protected Class<?> _rawDeserializationType(Annotated a)
    {
        if (a instanceof AnnotatedMethod) {
            AnnotatedMethod am = (AnnotatedMethod) a;
            // 27-Nov-2012, tatu: Bit nasty, as we are assuming
            //    things about method signatures here... but has to do
            if (am.getParameterCount() == 1) {
                return am.getRawParameterType(0);
            }
        }
        return a.getRawType();
    }

    protected JavaType _fullDeserializationType(AnnotatedMember am)
    {
        if (am instanceof AnnotatedMethod) {
            AnnotatedMethod method = (AnnotatedMethod) am;
            // 27-Nov-2012, tatu: Bit nasty, as we are assuming
            //    things about method signatures here... but has to do
            if (method.getParameterCount() == 1) {
                return ((AnnotatedMethod) am).getParameterType(0);
            }
        }
        return am.getType();
    }

    protected Class<?> _rawSerializationType(Annotated a)
    {
        // 27-Nov-2012, tatu: No work-arounds needed yet...
        return a.getRawType();
    }

    protected JavaType _fullSerializationType(AnnotatedMember am) {
        return am.getType();
    }

    protected Converter<Object,Object> _converter(XmlAdapter<?,?> adapter, boolean forSerialization)
    {
        TypeFactory tf = getTypeFactory();
        JavaType adapterType = tf.constructType(adapter.getClass());
        JavaType[] pt = tf.findTypeParameters(adapterType, XmlAdapter.class);
        // Order of type parameters for Converter is reverse between serializer, deserializer,
        // whereas JAXB just uses single ordering
        if (forSerialization) {
            return new AdapterConverter(adapter, pt[1], pt[0], forSerialization);
        }
        return new AdapterConverter(adapter, pt[0], pt[1], forSerialization);
    }

    protected Class<?> _getTypeFromXmlElement(Annotated a) {
        XmlElement annotation = findAnnotation(XmlElement.class, a, false, false, false);
        if (annotation != null) {
            // Further, JAXB has peculiar notion of declaring intermediate (and, for the
            // most part, useless) type... So basically we betterjust ignore type if there
            // is adapter annotation (we could check to see if intermediate type is compatible,
            // but let's not yet bother)
            if (a.getAnnotation(XmlJavaTypeAdapter.class) != null) {
                return null;
            }
            Class<?> type = annotation.type();
            if (type != XmlElement.DEFAULT.class) {
                return type;
            }
        }
        return null;
    }
}
