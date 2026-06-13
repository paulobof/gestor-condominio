package br.com.condominio.feature.whatsapp;

import br.com.condominio.feature.user.event.MemberEmailChangedEvent;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reage ao evento {@link MemberEmailChangedEvent} (AFTER_COMMIT) e enfileira uma mensagem WhatsApp
 * informando o morador que seu e-mail de acesso foi alterado.
 *
 * <p>Sem {@code @Transactional}: a chamada HTTP ao Evolution ({@code client.send}) não pode rodar
 * dentro de transação (CLAUDE.md). Cada escrita gerencia a própria transação — {@code outbox.*} via
 * {@link WhatsAppOutboxService}. Padrão idêntico ao {@link PasswordResetEventListener}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberEmailChangedEventListener {

  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMemberEmailChanged(MemberEmailChangedEvent e) {
    if (e.phone() == null || e.phone().isBlank()) {
      log.warn(
          "MemberEmailChanged ignorado userId={} sem phone (morador sem telefone cadastrado)",
          e.memberUserId());
      return;
    }
    Map<String, Object> data =
        Map.of("greetingName", e.greetingName() == null ? "" : e.greetingName());
    sendAndRecord(e.phone(), data);
  }

  /**
   * Enfileira na outbox e tenta enviar imediatamente. Se sucesso, marca SENT. Se falha, marca
   * FAILED — o scheduler reprocessa.
   *
   * <p>Sem {@code @Transactional}: chamada HTTP não pode rodar dentro de transação (CLAUDE.md).
   */
  private void sendAndRecord(String toPhone, Map<String, Object> data) {
    WhatsAppOutboxEntry entry =
        outbox.enqueue(toPhone, WhatsAppTemplate.MEMBER_EMAIL_CHANGED, data);
    Instant now = Instant.now();
    try {
      client.send(toPhone, WhatsAppTemplate.MEMBER_EMAIL_CHANGED, data);
      outbox.markSent(entry.getId(), now);
      log.info(
          "whatsapp.send.success template={} outboxId={}",
          WhatsAppTemplate.MEMBER_EMAIL_CHANGED,
          entry.getId());
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn(
          "whatsapp.send.failure template={} outboxId={} reason={}",
          WhatsAppTemplate.MEMBER_EMAIL_CHANGED,
          entry.getId(),
          ex.getMessage());
    }
  }
}
