package br.com.condominio.feature.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gerencia a outbox de WhatsApp. Lifecycle: enqueue (PENDING) → markSent/markFailed → cleanup (90
 * dias via {@code WhatsAppOutboxCleanupScheduler}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOutboxService {

  private final WhatsAppOutboxRepository repo;
  private final ObjectMapper objectMapper;

  @Transactional
  public WhatsAppOutboxEntry enqueue(
      String toPhone, WhatsAppTemplate template, Map<String, Object> data) {
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(data == null ? Map.of() : data);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Falha serializando payload do template " + template, e);
    }
    WhatsAppOutboxEntry entry = WhatsAppOutboxEntry.create(toPhone, template, payloadJson);
    return repo.save(entry);
  }

  @Transactional
  public void markSent(UUID id, Instant now) {
    repo.findById(id)
        .ifPresentOrElse(
            e -> {
              e.markSent(now);
              repo.save(e);
            },
            () -> log.warn("markSent: outbox entry {} nao encontrada", id));
  }

  @Transactional
  public void markFailed(UUID id, String reason, Instant now) {
    repo.findById(id)
        .ifPresentOrElse(
            e -> {
              e.markFailed(reason, now);
              repo.save(e);
            },
            () -> log.warn("markFailed: outbox entry {} nao encontrada", id));
  }

  @Transactional(readOnly = true)
  public List<WhatsAppOutboxEntry> listRetryable(int maxAttempts, int pageSize) {
    return repo.findRetryable(maxAttempts, PageRequest.of(0, pageSize));
  }
}
