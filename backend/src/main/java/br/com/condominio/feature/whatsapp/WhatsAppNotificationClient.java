package br.com.condominio.feature.whatsapp;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Map;
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
 * Cliente do Evolution GO (gateway WhatsApp do projeto). Renderiza o texto no backend ({@link
 * WhatsAppMessageRenderer}), normaliza o telefone ({@link PhoneNumberNormalizer}) e faz {@code POST
 * {baseUrl}/send/text} autenticado por {@code apikey} (o token da instância seleciona qual número
 * envia), com body {@code {number, text}}. Bloqueante ({@code .block()} com timeout). Resilience4j
 * cuida de retry e circuit breaker; em falha lança {@link WhatsAppSendException} para o listener
 * marcar a outbox como FAILED.
 */
@Component
@Slf4j
public class WhatsAppNotificationClient {

  private final WhatsAppProperties props;
  private final WebClient webClient;
  private final WhatsAppMessageRenderer renderer;
  private final PhoneNumberNormalizer normalizer;

  public WhatsAppNotificationClient(
      WhatsAppProperties props,
      @Qualifier("whatsappWebClient") WebClient webClient,
      WhatsAppMessageRenderer renderer,
      PhoneNumberNormalizer normalizer) {
    this.props = props;
    this.webClient = webClient;
    this.renderer = renderer;
    this.normalizer = normalizer;
    log.info(
        "WhatsAppNotificationClient (Evolution) inicializado: instance={} timeoutMs={}",
        props.getInstance(),
        props.getTimeoutMs());
  }

  /**
   * Envia mensagem com template e data. Validação de telefone/template ({@link
   * WhatsAppSendException}) ocorre antes da chamada HTTP. Falha de transporte ou resposta não-2xx
   * também lança {@link WhatsAppSendException} (gatilha retry).
   */
  @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendFallback")
  @Retry(name = "whatsapp")
  public void send(String toPhone, WhatsAppTemplate template, Map<String, Object> data) {
    // JID de grupo (ex.: 1203...@g.us) não é telefone — vai direto, sem normalizar pra DDI.
    String number =
        toPhone != null && toPhone.contains("@") ? toPhone : normalizer.toEvolutionNumber(toPhone);
    String text = renderer.render(template, data);
    String uri = props.getBaseUrl() + "/send/text";
    try {
      webClient
          .post()
          .uri(uri)
          .header("apikey", props.getApiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("number", number, "text", text))
          .retrieve()
          .toBodilessEntity()
          .block(props.timeout());
    } catch (WebClientResponseException e) {
      HttpStatusCode code = e.getStatusCode();
      log.warn(
          "Evolution retornou {} para template={} phoneRedacted={} body={}",
          code,
          template,
          redactPhone(number),
          e.getResponseBodyAsString());
      throw new WhatsAppSendException(
          "Evolution retornou " + code + " para template " + template, e);
    } catch (RuntimeException e) {
      log.warn(
          "Falha enviando WhatsApp template={} phoneRedacted={}: {}",
          template,
          redactPhone(number),
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
