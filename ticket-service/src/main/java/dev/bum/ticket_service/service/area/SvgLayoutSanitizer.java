package dev.bum.ticket_service.service.area;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;
import java.util.regex.Pattern;

/** Rebuilds a static SVG from allowed elements; uploaded scripts, CSS and URLs are never copied. */
@Component
public class SvgLayoutSanitizer {
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final int MAX_LENGTH = 2_000_000;
    private static final int MAX_DEPTH = 64;
    private static final Set<String> ELEMENTS = Set.of(
            "svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
            "text", "tspan", "title", "desc", "defs", "linearGradient", "radialGradient", "stop", "clipPath");
    private static final Set<String> ATTRIBUTES = Set.of(
            "id", "class", "viewBox", "width", "height", "preserveAspectRatio", "transform",
            "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "d", "points",
            "dx", "dy", "rotate", "textLength", "lengthAdjust", "fill", "stroke", "stroke-width",
            "fill-opacity", "stroke-opacity", "opacity", "fill-rule", "clip-rule", "clip-path",
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset",
            "font-size", "font-family", "font-weight", "font-style", "text-anchor", "dominant-baseline",
            "letter-spacing", "word-spacing", "visibility", "display", "vector-effect",
            "gradientUnits", "gradientTransform", "spreadMethod", "fx", "fy", "fr", "offset",
            "stop-color", "stop-opacity", "clipPathUnits",
            "data-layout-key", "data-area-name", "data-grade", "data-price");
    private static final Set<String> STYLE_PROPERTIES = Set.of(
            "fill", "stroke", "stroke-width", "fill-opacity", "stroke-opacity", "opacity",
            "fill-rule", "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset",
            "font-size", "font-family", "font-weight", "font-style", "text-anchor", "dominant-baseline",
            "letter-spacing", "word-spacing", "visibility", "display", "stop-color", "stop-opacity");
    private static final Pattern LOCAL_REFERENCE = Pattern.compile("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)");
    private static final Pattern PRESENTATION_VALUE = Pattern.compile("[A-Za-z0-9#.,%+\\-\\s]+|(?:rgb|rgba|hsl|hsla)\\([0-9.,%+\\-\\s]+\\)");

    public String sanitizeForDisplay(String svg) {
        try {
            return sanitize(svg);
        } catch (IllegalArgumentException e) {
            // A malformed legacy layout must not be returned as raw markup.
            return "";
        }
    }

    public String sanitize(String svg) {
        if (svg == null || svg.isBlank() || svg.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("SVG 내용이 비어 있거나 허용 크기를 초과했습니다.");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Document source = builder.parse(new InputSource(new StringReader(svg)));
            Element root = source.getDocumentElement();
            if (!"svg".equals(root.getLocalName()) || !allowedNamespace(root)) {
                throw new IllegalArgumentException("SVG 루트 태그가 올바르지 않습니다.");
            }
            Document clean = builder.newDocument();
            clean.appendChild(copyElement(root, clean, 0));

            TransformerFactory transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformers.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(clean), new StreamResult(output));
            return output.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("안전한 SVG 배치도로 처리할 수 없습니다.", e);
        }
    }

    private Element copyElement(Element source, Document target, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("SVG 중첩 깊이를 초과했습니다.");
        Element clean = target.createElementNS(SVG_NS, source.getLocalName());
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (attribute.getNamespaceURI() != null) continue;
            String name = attribute.getNodeName();
            String value = attribute.getNodeValue();
            if ("style".equals(name)) {
                copyPresentationStyle(value, clean);
            } else if (ATTRIBUTES.contains(name) && allowedValue(name, value)) {
                clean.setAttribute(name, value);
            }
        }
        for (Node child = source.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (allowedNamespace(element) && ELEMENTS.contains(element.getLocalName())) {
                    clean.appendChild(copyElement(element, target, depth + 1));
                }
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                clean.appendChild(target.createTextNode(child.getNodeValue()));
            }
        }
        return clean;
    }

    private boolean allowedNamespace(Element element) {
        return element.getNamespaceURI() == null || SVG_NS.equals(element.getNamespaceURI());
    }

    private boolean allowedValue(String name, String value) {
        if (name.equals("fill") || name.equals("stroke") || name.equals("clip-path")) {
            return PRESENTATION_VALUE.matcher(value.trim()).matches() || LOCAL_REFERENCE.matcher(value.trim()).matches();
        }
        return true;
    }

    // Convert only simple presentation declarations to attributes. No stylesheet or raw style survives.
    private void copyPresentationStyle(String style, Element target) {
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String name = declaration.substring(0, colon).trim();
            String value = declaration.substring(colon + 1).trim();
            if (STYLE_PROPERTIES.contains(name) && PRESENTATION_VALUE.matcher(value).matches()) {
                target.setAttribute(name, value);
            }
        }
    }
}
