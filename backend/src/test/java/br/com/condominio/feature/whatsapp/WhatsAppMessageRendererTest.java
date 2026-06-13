package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppMessageRendererTest {

  private final WhatsAppMessageRenderer renderer = new WhatsAppMessageRenderer();

  @Test
  void renderizaPasswordResetComVariaveis() {
    String msg =
        renderer.render(
            WhatsAppTemplate.PASSWORD_RESET,
            Map.of(
                "greetingName", "Paulo",
                "link", "https://app/reset?token=abc",
                "ttlMinutes", 30));
    assertThat(msg)
        .contains("Paulo")
        .contains("https://app/reset?token=abc")
        .contains("30 min")
        .contains("HELBOR TRILOGY HOME");
  }

  @Test
  void renderizaPasswordChanged() {
    String msg =
        renderer.render(WhatsAppTemplate.PASSWORD_CHANGED, Map.of("greetingName", "Maria"));
    assertThat(msg)
        .contains("Maria")
        .contains("alterada com sucesso")
        .contains("HELBOR TRILOGY HOME");
  }

  @Test
  void renderizaInactivityWarning() {
    String msg =
        renderer.render(
            WhatsAppTemplate.INACTIVITY_WARNING, Map.of("greetingName", "João", "daysLeft", 7));
    assertThat(msg).contains("João").contains("7 dias").contains("anonimizada");
  }

  @Test
  void aceitaGreetingNameVazio() {
    String msg =
        renderer.render(
            WhatsAppTemplate.PASSWORD_RESET,
            Map.of("greetingName", "", "link", "https://x", "ttlMinutes", 15));
    assertThat(msg).contains("https://x").contains("15 min");
  }

  @Test
  void lancaQuandoCampoObrigatorioAusente() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    WhatsAppTemplate.PASSWORD_RESET,
                    Map.of("greetingName", "Paulo", "ttlMinutes", 30)))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageContaining("link");
  }

  @Test
  void lancaQuandoDataNula() {
    assertThatThrownBy(() -> renderer.render(WhatsAppTemplate.PASSWORD_CHANGED, null))
        .isInstanceOf(WhatsAppSendException.class);
  }

  @Test
  void renderizaMemberEmailChanged() {
    String msg =
        renderer.render(WhatsAppTemplate.MEMBER_EMAIL_CHANGED, Map.of("greetingName", "Carlos"));
    assertThat(msg).contains("Carlos").contains("e-mail de acesso").contains("HELBOR TRILOGY HOME");
  }

  @Test
  void memberEmailChanged_aceitaGreetingNameVazio() {
    String msg = renderer.render(WhatsAppTemplate.MEMBER_EMAIL_CHANGED, Map.of("greetingName", ""));
    assertThat(msg).contains("e-mail de acesso").contains("HELBOR TRILOGY HOME");
  }
}
