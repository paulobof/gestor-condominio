package br.com.condominio.shared.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

  private final HtmlSanitizer sanitizer = new HtmlSanitizer();

  @Test
  void removesScriptTags() {
    String dirty = "<p>Olá</p><script>alert('xss')</script>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("script").contains("<p>Olá</p>");
  }

  @Test
  void removesEventHandlers() {
    String dirty = "<p onclick=\"steal()\">clique</p>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("onclick");
  }

  @Test
  void keepsBasicFormattingAndSafeLinks() {
    String clean =
        "<p><strong>Portaria</strong></p><ul><li>24h</li></ul>"
            + "<a href=\"tel:+551130000000\">ligar</a>";
    String result = sanitizer.sanitize(clean);
    assertThat(result).contains("<strong>").contains("<ul>").contains("<li>").contains("href");
  }

  @Test
  void dropsJavascriptUrls() {
    String dirty = "<a href=\"javascript:alert(1)\">x</a>";
    assertThat(sanitizer.sanitize(dirty)).doesNotContain("javascript:");
  }
}
