package br.com.condominio.feature.activity;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Ponto único para os services avisarem uma atividade. Publica um {@link ActivityEvent}; o {@link
 * ActivityWhatsAppListener} envia pro grupo em AFTER_COMMIT (não afeta a transação de negócio).
 * {@code label} deve ser SEM PII.
 */
@Component
@RequiredArgsConstructor
public class ActivityNotifier {

  private final ApplicationEventPublisher publisher;

  public void notify(ActivityAction action, String entityType, String label, UUID actorUserId) {
    publisher.publishEvent(new ActivityEvent(action, entityType, label, actorUserId));
  }
}
