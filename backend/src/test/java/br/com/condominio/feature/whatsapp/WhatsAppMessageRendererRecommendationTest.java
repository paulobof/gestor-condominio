package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppMessageRendererRecommendationTest {

  private final WhatsAppMessageRenderer renderer = new WhatsAppMessageRenderer();

  @Test
  void consent_rendersAllVars() {
    String text =
        renderer.render(
            WhatsAppTemplate.RECOMMENDATION_CONSENT,
            Map.of(
                "greetingName", "Maria",
                "recommenderName", "João",
                "serviceName", "Pintor",
                "link", "https://app/indicacoes/pendentes"));
    assertThat(text)
        .contains("Maria")
        .contains("João")
        .contains("Pintor")
        .contains("HELBOR TRILOGY HOME")
        .contains("https://app/indicacoes/pendentes");
  }

  @Test
  void consent_missingField_throws() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    WhatsAppTemplate.RECOMMENDATION_CONSENT, Map.of("greetingName", "Maria")))
        .isInstanceOf(WhatsAppSendException.class);
  }
}
