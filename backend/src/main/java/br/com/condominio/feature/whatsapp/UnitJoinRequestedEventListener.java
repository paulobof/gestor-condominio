package br.com.condominio.feature.whatsapp;

import br.com.condominio.feature.registration.event.UnitJoinRequestedEvent;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reage ao {@link UnitJoinRequestedEvent} (AFTER_COMMIT) avisando o master da unidade que alguém
 * pediu acesso e depende da aprovação dele.
 *
 * <p>Sem {@code @Transactional}: a chamada HTTP ao Evolution não pode rodar dentro de transação
 * (CLAUDE.md). Mesmo padrão do {@link MemberEmailChangedEventListener}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnitJoinRequestedEventListener {

  private static final WhatsAppTemplate TEMPLATE = WhatsAppTemplate.UNIT_JOIN_REQUEST;

  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUnitJoinRequested(UnitJoinRequestedEvent e) {
    if (e.masterPhone() == null || e.masterPhone().isBlank()) {
      log.warn(
          "UnitJoinRequested ignorado requestUserId={} — master sem telefone", e.requestUserId());
      return;
    }
    Map<String, Object> data =
        Map.of(
            "greetingName", e.masterGreetingName() == null ? "" : e.masterGreetingName(),
            "requesterName", e.requesterName() == null ? "" : e.requesterName(),
            "unitCode", e.unitCode() == null ? "" : e.unitCode());
    sendAndRecord(e.masterPhone(), data);
  }

  private void sendAndRecord(String toPhone, Map<String, Object> data) {
    WhatsAppOutboxEntry entry = outbox.enqueue(toPhone, TEMPLATE, data);
    Instant now = Instant.now();
    try {
      client.send(toPhone, TEMPLATE, data);
      outbox.markSent(entry.getId(), now);
      log.info("whatsapp.send.success template={} outboxId={}", TEMPLATE, entry.getId());
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn(
          "whatsapp.send.failure template={} outboxId={} reason={}",
          TEMPLATE,
          entry.getId(),
          ex.getMessage());
    }
  }
}
