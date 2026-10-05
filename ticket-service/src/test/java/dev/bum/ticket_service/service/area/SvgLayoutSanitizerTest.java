package dev.bum.ticket_service.service.area;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvgLayoutSanitizerTest {
    private final SvgLayoutSanitizer sanitizer = new SvgLayoutSanitizer();

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
