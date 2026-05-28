package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
  private WhatsAppProperties props;

  private static final byte[] KEY_V1_BYTES =
      "test-key-v1-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa"
          .substring(0, 32)
          .getBytes(StandardCharsets.UTF_8);

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    props = new WhatsAppProperties();
    props.setWebhookUrl(server.url("/send-message").toString());
    props.setHmacKeys("v1:" + Base64.getEncoder().encodeToString(KEY_V1_BYTES));
    props.setHmacActiveKid("v1");
    props.setTimeoutMs(3000);

    WebClient wc =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();
    client = new WhatsAppNotificationClient(props, wc);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void buildSignedBodyIncluiCamposCanonicosEordemAlfabetica() {
    String body =
        client.buildSignedBody(
            "+5511999999999", WhatsAppTemplate.PASSWORD_RESET, Map.of("link", "https://x"));
    assertThat(body).contains("\"data\":");
    assertThat(body).contains("\"jti\":");
    assertThat(body).contains("\"template\":\"PASSWORD_RESET\"");
    assertThat(body).contains("\"timestamp\":");
    assertThat(body).contains("\"to\":\"+5511999999999\"");
    int posData = body.indexOf("\"data\":");
    int posJti = body.indexOf("\"jti\":");
    int posTemplate = body.indexOf("\"template\":");
    int posTimestamp = body.indexOf("\"timestamp\":");
    int posTo = body.indexOf("\"to\":");
    assertThat(posData).isLessThan(posJti);
    assertThat(posJti).isLessThan(posTemplate);
    assertThat(posTemplate).isLessThan(posTimestamp);
    assertThat(posTimestamp).isLessThan(posTo);
  }

  @Test
  void signProduzHmacSha256HexDoBody() throws Exception {
    String body = "{\"a\":1}";
    String sig = client.sign(body);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY_V1_BYTES, "HmacSHA256"));
    byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : expected) sb.append(String.format("%02x", b));
    assertThat(sig).isEqualTo(sb.toString());
  }

  @Test
  void sendEnviaPostComHeadersHmacKidEcontentTypeJson() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));
    client.send("+5511988887777", WhatsAppTemplate.PASSWORD_RESET, Map.of("link", "https://x"));
    RecordedRequest req = server.takeRequest();
    assertThat(req.getMethod()).isEqualTo("POST");
    assertThat(req.getPath()).isEqualTo("/send-message");
    assertThat(req.getHeader("X-Hmac-Kid")).isEqualTo("v1");
    String sig = req.getHeader("X-Signature");
    assertThat(sig).startsWith("sha256=");
    assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    String body = req.getBody().readUtf8();
    Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
    assertThat(parsed).containsKeys("to", "template", "data", "jti", "timestamp");
    assertThat(parsed.get("to")).isEqualTo("+5511988887777");
    assertThat(parsed.get("template")).isEqualTo("PASSWORD_RESET");
  }

  @Test
  void sendComSignaturValidoBateContraHmacRecalculado() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));
    client.send("+5511977776666", WhatsAppTemplate.PASSWORD_CHANGED, Map.of());
    RecordedRequest req = server.takeRequest();
    String body = req.getBody().readUtf8();
    String headerSig = req.getHeader("X-Signature").substring("sha256=".length());

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY_V1_BYTES, "HmacSHA256"));
    byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : expected) sb.append(String.format("%02x", b));
    assertThat(headerSig).isEqualTo(sb.toString());
  }

  @Test
  void sendComBot5xxLancaWhatsAppSendException() {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertThatThrownBy(
            () ->
                client.send("+5511966665555", WhatsAppTemplate.PASSWORD_RESET, Map.of("link", "x")))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageContaining("503");
  }

  @Test
  void parsedHmacKeysFalhaSeActiveKidNaoExisteNasKeys() {
    WhatsAppProperties bad = new WhatsAppProperties();
    bad.setHmacKeys("v1:" + Base64.getEncoder().encodeToString(KEY_V1_BYTES));
    bad.setHmacActiveKid("v9");
    assertThatThrownBy(bad::parsedHmacKeys)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("v9");
  }

  @Test
  void parsedHmacKeysFalhaSeFormatoInvalido() {
    WhatsAppProperties bad = new WhatsAppProperties();
    bad.setHmacKeys("formato-invalido-sem-dois-pontos");
    assertThatThrownBy(bad::parsedHmacKeys).isInstanceOf(IllegalStateException.class);
  }
}
