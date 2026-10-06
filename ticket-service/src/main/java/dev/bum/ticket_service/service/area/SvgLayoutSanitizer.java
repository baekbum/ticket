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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Rebuilds a static SVG; simple local CSS is flattened into validated presentation attributes. */
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
            "letter-spacing", "word-spacing", "visibility", "display", "vector-effect", "pointer-events",
            "gradientUnits", "gradientTransform", "spreadMethod", "fx", "fy", "fr", "offset",
            "stop-color", "stop-opacity", "clipPathUnits",
            "data-layout-key", "data-area-name", "data-grade", "data-price");
    private static final Set<String> STYLE_PROPERTIES = Set.of(
            "fill", "stroke", "stroke-width", "fill-opacity", "stroke-opacity", "opacity",
            "fill-rule", "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset",
            "font-size", "font-family", "font-weight", "font-style", "text-anchor", "dominant-baseline",
            "letter-spacing", "word-spacing", "visibility", "display", "stop-color", "stop-opacity",
            "clip-path", "pointer-events");
    private static final Pattern LOCAL_REFERENCE = Pattern.compile("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)");
    private static final Pattern PRESENTATION_VALUE = Pattern.compile("[A-Za-z0-9#.,%+\\-\\s]+|(?:rgb|rgba|hsl|hsla)\\([0-9.,%+\\-\\s]+\\)");
    private static final Pattern CSS_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
    private static final Pattern SIMPLE_SELECTOR = Pattern.compile("(?:[.#][A-Za-z_][A-Za-z0-9_-]*|[A-Za-z][A-Za-z0-9]*)");
    private static final Pattern FONT = Pattern.compile(
            "(?:(normal|italic|oblique)\\s+)?(?:(normal|bold|[1-9]00)\\s+)?([0-9]+(?:\\.[0-9]+)?(?:px|pt|em|rem|%))\\s+([A-Za-z][A-Za-z0-9 ,_-]*)");

    private record StyleRule(String selector, Map<String, String> properties) {
        int specificity() {
            return selector.startsWith("#") ? 100 : selector.startsWith(".") ? 10 : 1;
        }

        boolean matches(Element element) {
            if (selector.startsWith("#")) return selector.substring(1).equals(element.getAttribute("id"));
            if (selector.startsWith(".")) {
                return List.of(element.getAttribute("class").trim().split("\\s+"))
                        .contains(selector.substring(1));
            }
            return selector.equals(element.getLocalName());
        }
    }

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
            clean.appendChild(copyElement(root, clean, 0, readStyleRules(root)));

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

    private Element copyElement(Element source, Document target, int depth, List<StyleRule> rules) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("SVG 중첩 깊이를 초과했습니다.");
        Element clean = target.createElementNS(SVG_NS, source.getLocalName());
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (attribute.getNamespaceURI() != null) continue;
            String name = attribute.getNodeName();
            String value = attribute.getNodeValue();
            if (ATTRIBUTES.contains(name) && allowedValue(name, value)) {
                clean.setAttribute(name, value);
            }
        }
        // CSS overrides presentation attributes; inline declarations override local CSS.
        for (StyleRule rule : rules) {
            if (rule.matches(source)) rule.properties().forEach(clean::setAttribute);
        }
        readPresentationStyle(source.getAttribute("style")).forEach(clean::setAttribute);
        for (Node child = source.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (allowedNamespace(element) && ELEMENTS.contains(element.getLocalName())) {
                    clean.appendChild(copyElement(element, target, depth + 1, rules));
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

    // Never retain a stylesheet: accept only local class, ID or element selectors and safe values.
    private List<StyleRule> readStyleRules(Element root) {
        List<StyleRule> rules = new ArrayList<>();
        var styles = root.getElementsByTagNameNS("*", "style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (!allowedNamespace(style)) continue;
            String css = style.getTextContent().replaceAll("(?s)/\\*.*?\\*/", "");
            // Imports, conditional rules and font-face are outside the static subset.
            if (css.contains("@")) continue;
            var matcher = CSS_RULE.matcher(css);
            while (matcher.find()) {
                Map<String, String> properties = readPresentationStyle(matcher.group(2));
                for (String selector : matcher.group(1).split(",")) {
                    selector = selector.trim();
                    if (SIMPLE_SELECTOR.matcher(selector).matches()) {
                        // Bound work for untrusted uploads, in addition to document size and depth limits.
                        if (rules.size() >= 256) throw new IllegalArgumentException("SVG 스타일 규칙 수를 초과했습니다.");
                        rules.add(new StyleRule(selector, properties));
                    }
                }
            }
        }
        rules.sort(Comparator.comparingInt(StyleRule::specificity));
        return rules;
    }

    private Map<String, String> readPresentationStyle(String style) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String name = declaration.substring(0, colon).trim();
            String value = declaration.substring(colon + 1).trim();
            if ("font".equals(name)) {
                var font = FONT.matcher(value);
                if (font.matches()) {
                    properties.put("font-style", font.group(1) == null ? "normal" : font.group(1));
                    properties.put("font-weight", font.group(2) == null ? "normal" : font.group(2));
                    properties.put("font-size", font.group(3));
                    properties.put("font-family", font.group(4));
                }
            } else if (STYLE_PROPERTIES.contains(name)
                    && (PRESENTATION_VALUE.matcher(value).matches()
                    || (Set.of("fill", "stroke", "clip-path").contains(name) && LOCAL_REFERENCE.matcher(value).matches()))) {
                properties.put(name, value);
            }
        }
        return properties;
    }
}
