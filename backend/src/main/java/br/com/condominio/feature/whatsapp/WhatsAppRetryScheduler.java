package br.com.condominio.feature.whatsapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reprocessa periodicamente entradas da outbox que estão PENDING ou FAILED com menos de
 * max_attempts. Roda em uma thread do {@code spring.task.scheduling.pool}.
 *
 * <p>Sem lock distribuído por enquanto: HML/prod rodam com {@code replicas=1}. Se for escalar para
 * múltiplas réplicas, adicionar ShedLock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppRetryScheduler {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final WhatsAppOutboxService outboxService;
  private final WhatsAppNotificationClient client;
  private final WhatsAppProperties props;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelayString = "${app.whatsapp.retry-interval-ms:60000}", initialDelay = 30_000)
  public void process() {
    List<WhatsAppOutboxEntry> batch = outboxService.listRetryable(props.getMaxRetries(), 50);
    if (batch.isEmpty()) return;
    log.debug("whatsapp.retry batch size={}", batch.size());
    for (WhatsAppOutboxEntry entry : batch) {
      Instant now = Instant.now();
      try {
        Map<String, Object> data = objectMapper.readValue(entry.getPayload(), MAP_TYPE);
        client.send(entry.getToPhone(), entry.getTemplate(), data);
        outboxService.markSent(entry.getId(), now);
      } catch (RuntimeException | java.io.IOException ex) {
        outboxService.markFailed(entry.getId(), ex.getMessage(), now);
      }
    }
  }
}
