package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class WhatsAppNotificationClientTest {

  private MockWebServer server;
  private WhatsAppNotificationClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    WhatsAppProperties props = new WhatsAppProperties();
    // server.url("") inclui a barra final; removemos para não duplicar com o path do sendText.
    String base = server.url("").toString();
    props.setBaseUrl(base.substring(0, base.length() - 1));
    props.setApiKey("secret-key");
    props.setInstance("trilogy");
    props.setTimeoutMs(3000);

    WebClient wc =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();
    client =
        new WhatsAppNotificationClient(
            props, wc, new WhatsAppMessageRenderer(), new PhoneNumberNormalizer());
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void enviaPostParaSendTextComApiKeyEbodyNumberText() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(201));

    client.send(
        "+5511988887777",
        WhatsAppTemplate.PASSWORD_RESET,
        Map.of("greetingName", "Paulo", "link", "https://app/reset?token=abc", "ttlMinutes", 30));

    RecordedRequest req = server.takeRequest();
    assertThat(req.getMethod()).isEqualTo("POST");
    assertThat(req.getPath()).isEqualTo("/message/sendText/trilogy");
    assertThat(req.getHeader("apikey")).isEqualTo("secret-key");
    assertThat(req.getHeader("Content-Type")).startsWith("application/json");

    Map<?, ?> body = new ObjectMapper().readValue(req.getBody().readUtf8(), Map.class);
    assertThat(body.get("number")).isEqualTo("5511988887777");
    assertThat(body.get("text").toString())
        .contains("Paulo")
        .contains("https://app/reset?token=abc")
        .contains("30 min");
  }

  @Test
  void normalizaTelefoneSemDdi() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));

    client.send("11988887777", WhatsAppTemplate.PASSWORD_CHANGED, Map.of("greetingName", "Maria"));

    RecordedRequest req = server.takeRequest();
    Map<?, ?> body = new ObjectMapper().readValue(req.getBody().readUtf8(), Map.class);
    assertThat(body.get("number")).isEqualTo("5511988887777");
  }

  @Test
  void evolution4xxLancaWhatsAppSendException() {
    server.enqueue(new MockResponse().setResponseCode(400));
    assertThatThrownBy(
            () ->
                client.send(
                    "+5511966665555",
                    WhatsAppTemplate.PASSWORD_RESET,
                    Map.of("greetingName", "X", "link", "https://x", "ttlMinutes", 30)))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageContaining("400");
  }

  @Test
  void evolution5xxLancaWhatsAppSendException() {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertThatThrownBy(
            () ->
                client.send(
                    "+5511966665555",
                    WhatsAppTemplate.PASSWORD_RESET,
                    Map.of("greetingName", "X", "link", "https://x", "ttlMinutes", 30)))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageContaining("503");
  }

  @Test
  void telefoneInvalidoLancaAntesDeChamarEvolution() {
    assertThatThrownBy(
            () ->
                client.send(
                    "123",
                    WhatsAppTemplate.PASSWORD_RESET,
                    Map.of("greetingName", "X", "link", "https://x", "ttlMinutes", 30)))
        .isInstanceOf(WhatsAppSendException.class);
    assertThat(server.getRequestCount()).isZero();
  }
}
