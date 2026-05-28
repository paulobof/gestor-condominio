package br.com.condominio.feature.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Cliente HTTP para o webhook do bot WhatsApp do Paulo. Assina o body com HMAC-SHA256 e envia POST
 * síncrono (.block() com timeout). Resilience4j cuida de retry e circuit breaker.
 *
 * <p>Payload canônico (chaves ordenadas) inclui {@code timestamp} (ISO-8601) e {@code jti} (UUID
 * único) para o bot detectar replay (janela de {@link
 * WhatsAppProperties#getAntiReplayWindowSeconds} segundos).
 */
@Component
@Slf4j
public class WhatsAppNotificationClient {

  private final WhatsAppProperties props;
  private final WebClient webClient;
  private final ObjectMapper canonicalMapper;
  private final Map<String, byte[]> hmacKeys;

  public WhatsAppNotificationClient(
      WhatsAppProperties props, @Qualifier("whatsappWebClient") WebClient webClient) {
    this.props = props;
    this.webClient = webClient;
    this.canonicalMapper = new ObjectMapper();
    this.canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.hmacKeys = props.parsedHmacKeys(); // valida config no startup
    log.info(
        "WhatsAppNotificationClient inicializado: kids={} activeKid={} timeoutMs={}",
        hmacKeys.keySet(),
        props.getHmacActiveKid(),
        props.getTimeoutMs());
  }

  /**
   * Envia mensagem com template e data. Bloqueante: usa WebClient mas chama {@code .block()} com
   * timeout. Lança {@link WhatsAppSendException} em falha (gatilha retry).
   */
  @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendFallback")
  @Retry(name = "whatsapp")
  public void send(String toPhone, WhatsAppTemplate template, Map<String, Object> data) {
    String body = buildSignedBody(toPhone, template, data);
    String signature = sign(body);
    String kid = props.getHmacActiveKid();
    try {
      webClient
          .post()
          .uri(props.getWebhookUrl())
          .header("X-Signature", "sha256=" + signature)
          .header("X-Hmac-Kid", kid)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .toBodilessEntity()
          .block(props.timeout());
    } catch (WebClientResponseException e) {
      HttpStatusCode code = e.getStatusCode();
      log.warn(
          "WhatsApp bot retornou {} para template={} phoneRedacted={} body={}",
          code,
          template,
          redactPhone(toPhone),
          e.getResponseBodyAsString());
      throw new WhatsAppSendException("Bot retornou " + code + " para template " + template, e);
    } catch (RuntimeException e) {
      log.warn(
          "Falha enviando WhatsApp template={} phoneRedacted={}: {}",
          template,
          redactPhone(toPhone),
          e.getMessage());
      throw new WhatsAppSendException(
          "Falha de transporte enviando WhatsApp: " + e.getMessage(), e);
    }
  }

  /** Fallback do circuit-breaker. Loga e propaga — outbox marca FAILED no caller (listener). */
  @SuppressWarnings("unused")
  private void sendFallback(
      String toPhone, WhatsAppTemplate template, Map<String, Object> data, Throwable e) {
    log.warn(
        "WhatsApp circuit-breaker fallback acionado template={} cause={}", template, e.toString());
    throw new WhatsAppSendException("Circuit breaker aberto ou retry esgotado", e);
  }

  /** Constrói o body canônico (chaves ordenadas) que será assinado. */
  String buildSignedBody(String toPhone, WhatsAppTemplate template, Map<String, Object> data) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("data", data);
    body.put("jti", UUID.randomUUID().toString());
    body.put("template", template.name());
    body.put("timestamp", Instant.now().toString());
    body.put("to", toPhone);
    try {
      return canonicalMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Falha serializando payload WhatsApp", e);
    }
  }

  /** HMAC-SHA256 hex do body com a chave ativa. Visibilidade package p/ testes. */
  String sign(String body) {
    return signWith(body, props.getHmacActiveKid());
  }

  String signWith(String body, String kid) {
    byte[] key = hmacKeys.get(kid);
    if (key == null) {
      throw new IllegalStateException("kid desconhecido: " + kid);
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(sig.length * 2);
      for (byte b : sig) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Falha HMAC", e);
    }
  }

  private static String redactPhone(String phone) {
    if (phone == null || phone.length() < 4) return "***";
    return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
  }

  @Configuration
  @EnableConfigurationProperties(WhatsAppProperties.class)
  static class WebClientConfig {

    @Bean("whatsappWebClient")
    public WebClient whatsappWebClient(WhatsAppProperties props) {
      HttpClient httpClient =
          HttpClient.create().responseTimeout(props.timeout()).followRedirect(false);
      return WebClient.builder()
          .clientConnector(new ReactorClientHttpConnector(httpClient))
          .build();
    }
  }
}
