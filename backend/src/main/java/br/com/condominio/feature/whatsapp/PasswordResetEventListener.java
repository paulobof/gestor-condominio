package br.com.condominio.feature.whatsapp;

import br.com.condominio.feature.password.PasswordResetProperties;
import br.com.condominio.feature.password.PasswordResetTokenRepository;
import br.com.condominio.feature.password.event.PasswordResetCompletedEvent;
import br.com.condominio.feature.password.event.PasswordResetRequestedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reage aos eventos de domínio de reset publicados em AFTER_COMMIT, garante idempotência
 * (delivered_at) e delega o envio efetivo ao {@link WhatsAppNotificationClient}. Toda interação com
 * a outbox marca SENT/FAILED para que o {@link WhatsAppRetryScheduler} possa reprocessar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEventListener {

  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;
  private final PasswordResetProperties resetProps;
  private final PasswordResetTokenRepository tokenRepo;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordResetRequested(PasswordResetRequestedEvent e) {
    if (e.phone() == null || e.phone().isBlank()) {
      log.warn(
          "PasswordResetRequested ignorado userId={} sem phone (não deveria acontecer)",
          e.userId());
      return;
    }
    String link = resetProps.buildResetLink(e.rawToken());
    Map<String, Object> data =
        Map.of(
            "greetingName",
            e.greetingName() == null ? "" : e.greetingName(),
            "link",
            link,
            "ttlMinutes",
            e.ttlMinutes());
    sendAndRecord(e.phone(), WhatsAppTemplate.PASSWORD_RESET, data, e.tokenId());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordResetCompleted(PasswordResetCompletedEvent e) {
    if (e.phone() == null || e.phone().isBlank()) return;
    Map<String, Object> data =
        Map.of("greetingName", e.greetingName() == null ? "" : e.greetingName());
    sendAndRecord(e.phone(), WhatsAppTemplate.PASSWORD_CHANGED, data, null);
  }

  /**
   * Enfileira na outbox e tenta enviar imediatamente. Se sucesso, marca SENT + delivered_at. Se
   * falha, marca FAILED — o scheduler reprocessa.
   *
   * <p>Sem {@code @Transactional}: a chamada HTTP ao Evolution ({@code client.send}) não pode rodar
   * dentro de transação (CLAUDE.md). Cada escrita gerencia a sua própria transação — {@code
   * outbox.*} via {@link WhatsAppOutboxService} e {@code tokenRepo.markDelivered} via
   * {@code @Transactional} no próprio repositório. (Antes este método era {@code @Transactional},
   * mas, por ser invocado internamente no mesmo bean, o proxy era ignorado e o {@code
   * markDelivered} quebrava com {@code TransactionRequiredException}.)
   */
  private void sendAndRecord(
      String toPhone, WhatsAppTemplate template, Map<String, Object> data, UUID tokenId) {
    WhatsAppOutboxEntry entry = outbox.enqueue(toPhone, template, data);
    Instant now = Instant.now();
    try {
      client.send(toPhone, template, data);
      outbox.markSent(entry.getId(), now);
      if (tokenId != null) {
        tokenRepo.markDelivered(tokenId, now);
      }
      log.info("whatsapp.send.success template={} outboxId={}", template, entry.getId());
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn(
          "whatsapp.send.failure template={} outboxId={} reason={}",
          template,
          entry.getId(),
          ex.getMessage());
    }
  }
}
