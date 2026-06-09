package br.com.condominio.shared.html;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Sanitiza HTML vindo do editor rich text antes de persistir (defesa primária contra XSS — STRIDE).
 * Permite só tags de formatação básica e links seguros (http/https/tel/mailto). O frontend ainda
 * sanitiza no render (DOMPurify) como defesa em profundidade.
 */
@Component
public class HtmlSanitizer {

  private final Safelist safelist =
      Safelist.none()
          .addTags("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "a")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "http", "https", "tel", "mailto");

  public String sanitize(String html) {
    if (html == null) {
      return null;
    }
    return Jsoup.clean(html, safelist);
  }
}
