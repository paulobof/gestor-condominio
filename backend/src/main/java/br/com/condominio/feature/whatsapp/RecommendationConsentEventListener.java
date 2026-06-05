package br.com.condominio.feature.whatsapp;

import br.com.condominio.feature.recommendation.RecommendationProperties;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reage ao {@link RecommendationConsentRequestedEvent} publicado em AFTER_COMMIT e enfileira/envia
 * o WhatsApp de pedido de consentimento ao morador indicado. Marca SENT/FAILED na outbox para que o
 * {@link WhatsAppRetryScheduler} possa reprocessar.
 *
 * <p>Sem {@code @Transactional}: a chamada HTTP ao Evolution ({@code client.send}) não pode rodar
 * dentro de transação (CLAUDE.md). Cada escrita na outbox gerencia sua própria transação.
 *
 * <p>Registra {@link RecommendationProperties} (consumidor real, conforme convenção do projeto).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConsentEventListener {

  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;
  private final RecommendationProperties props;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onConsentRequested(RecommendationConsentRequestedEvent e) {
    if (e.residentPhone() == null || e.residentPhone().isBlank()) {
      log.warn("Consent ignorado recommendationId={} sem phone do morador", e.recommendationId());
      return;
    }
    Map<String, Object> data =
        Map.of(
            "greetingName",
            e.residentGreetingName() == null ? "" : e.residentGreetingName(),
            "recommenderName",
            e.recommenderName() == null ? "" : e.recommenderName(),
            "serviceName",
            e.serviceName(),
            "link",
            props.buildConsentLink());
    WhatsAppOutboxEntry entry =
        outbox.enqueue(e.residentPhone(), WhatsAppTemplate.RECOMMENDATION_CONSENT, data);
    Instant now = Instant.now();
    try {
      client.send(e.residentPhone(), WhatsAppTemplate.RECOMMENDATION_CONSENT, data);
      outbox.markSent(entry.getId(), now);
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn(
          "Falha ao enviar consentimento recommendationId={} outboxId={} reason={}",
          e.recommendationId(),
          entry.getId(),
          ex.getMessage());
    }
  }
}
