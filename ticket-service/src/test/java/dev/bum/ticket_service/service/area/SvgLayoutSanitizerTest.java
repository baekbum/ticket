package dev.bum.ticket_service.service.area;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvgLayoutSanitizerTest {
    private final SvgLayoutSanitizer sanitizer = new SvgLayoutSanitizer();

    @Test
    void preserves_legacy_layout_background_labels_and_fonts_without_stylesheets() throws Exception {
        String original;
        try (var input = getClass().getResourceAsStream("/layouts/kspo-layout-sample.svg")) {
            assertThat(input).isNotNull();
            original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String clean = sanitizer.sanitize(original);
        var document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(clean)));
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        assertThat(xpath.evaluate("//rect[@class='bg']/@fill", document)).isEqualTo("#cfe7f4");
        assertThat(xpath.evaluate("//text[@class='title']/@fill", document)).isEqualTo("#69a8d1");
        assertThat(xpath.evaluate("//text[@class='title']/@font-size", document)).isEqualTo("38px");
        assertThat(xpath.evaluate("//text[@class='title']/@font-weight", document)).isEqualTo("700");
        assertThat(xpath.evaluate("//text[@class='label'][1]/@fill", document)).isEqualTo("#fff");
        assertThat(xpath.evaluate("//text[@class='label'][1]/@pointer-events", document)).isEqualTo("none");
        assertThat(xpath.evaluate("//rect[@id='area-floor-A']/@fill", document)).isEqualTo("#456bd7");
        assertThat(clean).contains("좌석 배치도", "STAGE", "data-layout-key=\"A\"");
        assertThat(clean).doesNotContain("<style", " style=", "filter=", "drop-shadow");
        assertThat(sanitizer.sanitize(clean)).isEqualTo(clean);
    }

    @Test
    void flattens_only_safe_local_css_with_correct_precedence() {
        String clean = sanitizer.sanitize("""
                <svg xmlns="http://www.w3.org/2000/svg"><style>
                  #label { fill: #123456; }
                  .label { fill: #ffffff; font: 700 15px Arial, sans-serif; }
                  text { fill: #000000; }
                  .area { fill: url(https://evil.test/x); stroke: url(#paint); }
                  .label { filter: url(https://evil.test/x); background: url(https://evil.test/x); }
                </style><text id="label" class="label" fill="#aaaaaa">A</text>
                <text class="label" style="fill:#bbbbbb">B</text>
                <path class="area" d="M0 0L10 10"/></svg>
                """);
        assertThat(clean).contains("fill=\"#123456\"", "fill=\"#bbbbbb\"", "font-size=\"15px\"",
                "font-weight=\"700\"", "font-family=\"Arial, sans-serif\"", "stroke=\"url(#paint)\"");
        assertThat(clean).doesNotContain("<style", " style=", "evil.test", "filter=", "background=");
    }

    @Test
    void drops_imports_escaped_css_and_executable_font_values() {
        String clean = sanitizer.sanitize("""
                <svg xmlns="http://www.w3.org/2000/svg"><style>
                  @import url(https://evil.test/style);
                  .label { fill: red; }
                </style><style>
                  .label { fill: u\\rl(https://evil.test/paint); font: 15px url(https://evil.test/font); }
                </style><text class="label">Safe text</text></svg>
                """);
        assertThat(clean).contains("Safe text");
        assertThat(clean).doesNotContain("evil.test", "<style", "fill=", "font-family=", "url(");
    }

    @Test
    void removes_active_content_and_preserves_area_geometry() {
        String clean = sanitizer.sanitize("""
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                     viewBox="0 0 420 180" onload="alert(1)">
                  <style>@import url(https://evil.test/style);</style>
                  <script>alert(1)</script>
                  <foreignObject><div xmlns="http://www.w3.org/1999/xhtml" onclick="alert(1)">attack</div></foreignObject>
                  <a xlink:href="javascript:alert(1)"><text>attack</text></a>
                  <image href="https://evil.test/tracker"/>
                  <use xlink:href="https://evil.test/image.svg#x"/>
                  <rect id="area-vip" class="area vip" data-layout-key="VIP" data-area-name="VIP A"
                        data-price="165000" data-grade="VIP" x="20" y="20" width="180" height="120"
                        onclick="alert(1)" style="fill:#ff0000;stroke-width:2;background:url(https://evil.test)">
                    <animate attributeName="href" values="javascript:alert(1)"/>
                    <set attributeName="onload" to="alert(1)"/>
                    <title>VIP A</title>
                  </rect>
                </svg>
                """);
        assertThat(clean).contains("viewBox=\"0 0 420 180\"", "data-layout-key=\"VIP\"", "class=\"area vip\"",
                "width=\"180\"", "fill=\"#ff0000\"", "stroke-width=\"2\"", "<title>VIP A</title>");
        assertThat(clean).doesNotContain("script", "onload", "onclick", "foreignObject", "<a ", "<image",
                "<use", "<animate", "<set", "style=", "<style", "evil.test", "javascript", "attack");
        assertThat(sanitizer.sanitize(clean)).isEqualTo(clean);
    }

    @ParameterizedTest
    @ValueSource(strings = {"url(https://evil.test/x)", "url(//evil.test/x)", "url(data:image/svg+xml,x)",
            "url(javascript:alert(1))", "url(&#x23;x) url(https://evil.test/x)", "u\\rl(https://evil.test/x)"})
    void rejects_non_local_paint_references(String value) {
        assertThat(sanitizer.sanitize("<svg><path fill=\"" + value + "\" stroke=\"" + value
                + "\" clip-path=\"" + value + "\"/></svg>"))
                .doesNotContain("fill=", "stroke=", "clip-path=");
    }

    @Test
    void preserves_gradients_text_and_local_clipping() {
        String clean = sanitizer.sanitize("""
                <svg xmlns="http://www.w3.org/2000/svg"><defs>
                  <linearGradient id="paint"><stop offset="0%" stop-color="#fff"/></linearGradient>
                  <clipPath id="clip"><rect width="20" height="20"/></clipPath>
                </defs><path d="M0 0L20 20" fill="url(#paint)" clip-path="url(#clip)"/>
                <text x="10" y="10">VIP &amp; R</text></svg>
                """);
        assertThat(clean).contains("linearGradient", "fill=\"url(#paint)\"", "clip-path=\"url(#clip)\"", "VIP &amp; R");
    }

    @Test
    void drops_foreign_namespaces_and_processing_instructions() {
        assertThat(sanitizer.sanitize("""
                <?xml-stylesheet href="https://evil.test/style"?>
                <svg xmlns="http://www.w3.org/2000/svg"><svg xmlns="http://www.w3.org/1999/xhtml">
                <script>alert(1)</script></svg><rect xmlns:evil="urn:evil" evil:onload="alert(1)"/></svg>
                """)).doesNotContain("stylesheet", "evil", "script", "alert");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html/>", "<svg xmlns='http://www.w3.org/1999/xhtml'/>", "<svg><g></svg>",
            "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg>&x;</svg>",
            "<!DOCTYPE svg SYSTEM 'https://evil.test/svg.dtd'><svg/>"})
    void rejects_invalid_documents_and_external_entities(String svg) {
        assertThatThrownBy(() -> sanitizer.sanitize(svg)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sanitizer.sanitizeForDisplay(svg)).isEmpty();
    }

    @Test
    void escapes_text_before_html_insertion() {
        assertThat(sanitizer.sanitize("<svg><text><![CDATA[<img src=x onerror=alert(1)>]]></text></svg>"))
                .contains("&lt;img").doesNotContain("<img");
    }
}
