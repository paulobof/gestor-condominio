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
  void renderizaUnitJoinRequestParaOMaster() {
    String msg =
        renderer.render(
            WhatsAppTemplate.UNIT_JOIN_REQUEST,
            Map.of("greetingName", "Ana", "requesterName", "Maria", "unitCode", "702C"));
    assertThat(msg)
        .contains("Ana")
        .contains("Maria")
        .contains("702C")
        .contains("Moradores")
        .contains("HELBOR TRILOGY HOME");
  }

  @Test
  void unitJoinRequestSemUnitCodeFalha() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    WhatsAppTemplate.UNIT_JOIN_REQUEST,
                    Map.of("greetingName", "Ana", "requesterName", "Maria")))
        .isInstanceOf(WhatsAppSendException.class);
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

  @Test
  void renderizaConviteDeAcesso() {
    String msg =
        renderer.render(
            WhatsAppTemplate.ACCOUNT_INVITE,
            Map.of(
                "greetingName", "Ana",
                "link", "https://app/reset?token=abc&primeiro=1",
                "ttlHours", 72));
    assertThat(msg)
        .startsWith("Olá, Ana!")
        .contains("app de moradores do HELBOR TRILOGY HOME")
        .contains("https://app/reset?token=abc&primeiro=1")
        .contains("72 horas")
        .endsWith("Se o link expirar, peça um novo a quem fez o seu cadastro.")
        .doesNotContain("Esqueci minha senha");
  }

  @Test
  void conviteSemNomeCumprimentaSemNome() {
    String msg =
        renderer.render(
            WhatsAppTemplate.ACCOUNT_INVITE,
            Map.of("greetingName", "", "link", "https://x", "ttlHours", 72));
    assertThat(msg).startsWith("Olá!").doesNotContain("Olá, !").contains("https://x");
  }

  @Test
  void conviteSemLinkFalha() {
    assertThatThrownBy(
            () ->
                renderer.render(
                    WhatsAppTemplate.ACCOUNT_INVITE, Map.of("greetingName", "Ana", "ttlHours", 72)))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageContaining("link");
  }
}
