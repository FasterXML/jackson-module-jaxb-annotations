package com.fasterxml.jackson.module.jaxb.introspect;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.*;

import org.w3c.dom.Element;

/**
 * <p>Represents an atom:content element.</p>
 * <p/>
 * <p>Per RFC4287:</p>
 * <p/>
 * <pre>
 *  The "atom:content" element either contains or links to the content of
 *  the entry.  The content of atom:content is Language-Sensitive.
 * <p/>
 *  atomInlineTextContent =
 *     element atom:content {
 *        atomCommonAttributes,
 *        attribute type { "text" | "html" }?,
 *        (text)*
 *     }
 * <p/>
 *  atomInlineXHTMLContent =
 *     element atom:content {
 *        atomCommonAttributes,
 *        attribute type { "xhtml" },
 *        xhtmlDiv
 *     }
 *  atomInlineOtherContent =
 *     element atom:content {
 *        atomCommonAttributes,
 *        attribute type { atomMediaType }?,
 *        (text|anyElement)*
 *     }
 * <p/>
 *  atomOutOfLineContent =
 *     element atom:content {
 *        atomCommonAttributes,
 *        attribute type { atomMediaType }?,
 *        attribute src { atomUri },
 *        empty
 *     }
 * <p/>
 *  atomContent = atomInlineTextContent
 *   | atomInlineXHTMLContent
 *   | atomInlineOtherContent
 *   | atomOutOfLineContent
 * <p/>
 * </pre>
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1666 $
 */
@XmlRootElement(name = "content")
@XmlAccessorType(XmlAccessType.PROPERTY)
class Content extends CommonAttributes
{
   private String type;
   private MediaType mediaType;
   private String text;
   private Element element;
   private URI src;
   private List<Object> value;
   protected Object jaxbObject;

   @XmlAnyElement
   @XmlMixed
   public List<Object> getValue() { return value; }

   public void setValue(List<Object> value) { this.value = value; }

   @XmlAttribute
   public URI getSrc() { return src; }

   public void setSrc(URI src) { this.src = src; }

   /**
    * Mime type of the content
    */
   @XmlTransient
   public MediaType getType()
   {
      if (mediaType == null)
      {
         if (type.equals("html")) mediaType = MediaType.TEXT_HTML_TYPE;
         else if (type.equals("text")) mediaType = MediaType.TEXT_PLAIN_TYPE;
         else if (type.equals("xhtml")) mediaType = MediaType.APPLICATION_XHTML_XML_TYPE;
         else mediaType = MediaType.valueOf(type);
      }
      return mediaType;
   }

   public void setType(MediaType type)
   {
      mediaType = type;
      if (type.equals(MediaType.TEXT_PLAIN_TYPE)) this.type = "text";
      else if (type.equals(MediaType.TEXT_HTML_TYPE)) this.type = "html";
      else if (type.equals(MediaType.APPLICATION_XHTML_XML_TYPE)) this.type = "xhtml";
      else this.type = type.toString();
   }

   @XmlAttribute(name = "type")
   public String getRawType() { return type; }


   public void setRawType(String type) { this.type = type; }

   @XmlTransient
   public String getText()
   {
      if (value == null) return null;
      if (value.size() == 0) return null;
      if (text != null) return text;
      StringBuffer buf = new StringBuffer();
      for (Object obj : value)
      {
         if (obj instanceof String) buf.append(obj.toString());
      }
      text = buf.toString();
      return text;
   }

   public void setText(String text)
   {
      if (value == null) value = new ArrayList<Object>();
      if (this.text != null && value != null) value.clear();
      this.text = text;
      value.add(text);
   }

   /**
    * Get content as an XML Element if the content is XML.  Otherwise, this will just return null.
    */
   @XmlTransient
   public Element getElement()
   {
      if (value == null) return null;
      if (element != null) return element;
      for (Object obj : value)
      {
         if (obj instanceof Element)
         {
            element = (Element) obj;
            return element;
         }
      }
      return null;
   }

   /**
    * Set the content to an XML Element
    *
    * @param element
    */
   public void setElement(Element element)
   {
      if (value == null) value = new ArrayList<Object>();
      if (this.element != null && value != null) value.clear();
      this.element = element;
      value.add(element);
   }
}

@XmlAccessorType(XmlAccessType.PROPERTY)
class CommonAttributes
{
   private String language;
   private URI base;

   private Map<?,?> extensionAttributes = new HashMap<String,String>();

   @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
   public String getLanguage() {
      return language;
   }

   public void setLanguage(String language) {
      this.language = language;
   }

   @XmlAttribute(namespace = "http://www.w3.org/XML/1998/namespace")
   public URI getBase() {
      return base;
   }

   public void setBase(URI base) {
      this.base = base;
   }

   @XmlAnyAttribute
   public Map<?,?> getExtensionAttributes() {
      return extensionAttributes;
   }
}
