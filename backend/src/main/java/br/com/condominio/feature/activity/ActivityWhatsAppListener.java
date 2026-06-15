package br.com.condominio.feature.activity;

import br.com.condominio.feature.role.PermissionResolver;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.whatsapp.WhatsAppNotificationClient;
import br.com.condominio.feature.whatsapp.WhatsAppOutboxEntry;
import br.com.condominio.feature.whatsapp.WhatsAppOutboxService;
import br.com.condominio.feature.whatsapp.WhatsAppTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia um aviso de atividade (criação/edição/exclusão) pro grupo WhatsApp de admins, em
 * AFTER_COMMIT + {@code @Async} (não afeta a transação de negócio; é best-effort via outbox).
 * Mensagem SEM PII: ambiente + ação + tipo + rótulo do item + papel do autor.
 */
@Component
@Slf4j
@EnableConfigurationProperties(ActivityAlertProperties.class)
public class ActivityWhatsAppListener {

  private final ActivityAlertProperties props;
  private final WhatsAppOutboxService outbox;
  private final WhatsAppNotificationClient client;
  private final PermissionResolver permissionResolver;
  private final String envLabel;

  public ActivityWhatsAppListener(
      ActivityAlertProperties props,
      WhatsAppOutboxService outbox,
      WhatsAppNotificationClient client,
      PermissionResolver permissionResolver,
      @Value("${spring.profiles.active:dev}") String profile) {
    this.props = props;
    this.outbox = outbox;
    this.client = client;
    this.permissionResolver = permissionResolver;
    this.envLabel = mapEnv(profile);
  }

  // fallbackExecution=true: alguns publishers (ex.: DocumentService.upload) não rodam em transação;
  // sem isso o evento seria descartado. Em publisher transacional, segue valendo o AFTER_COMMIT
  // (rollback suprime o aviso).
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActivity(ActivityEvent e) {
    if (!props.isEnabled() || props.getGroupJid() == null || props.getGroupJid().isBlank()) {
      return;
    }
    String text = buildText(e);
    Map<String, Object> data = Map.of("text", text);
    WhatsAppOutboxEntry entry =
        outbox.enqueue(props.getGroupJid(), WhatsAppTemplate.ACTIVITY_ALERT, data);
    Instant now = Instant.now();
    try {
      client.send(props.getGroupJid(), WhatsAppTemplate.ACTIVITY_ALERT, data);
      outbox.markSent(entry.getId(), now);
    } catch (RuntimeException ex) {
      outbox.markFailed(entry.getId(), ex.getMessage(), now);
      log.warn("activity.alert.failure outboxId={} reason={}", entry.getId(), ex.getMessage());
    }
  }

  String buildText(ActivityEvent e) {
    String icon =
        switch (e.action()) {
          case CREATED -> "🟢";
          case UPDATED -> "✏️";
          case DELETED -> "🗑️";
        };
    String act =
        switch (e.action()) {
          case CREATED -> "Criado";
          case UPDATED -> "Editado";
          case DELETED -> "Excluído";
        };
    StringBuilder sb = new StringBuilder();
    sb.append(icon)
        .append(" [")
        .append(envLabel)
        .append("] ")
        .append(act)
        .append(" · ")
        .append(e.entityType());
    if (e.label() != null && !e.label().isBlank()) {
      sb.append(": '").append(e.label()).append("'");
    }
    String role = primaryRole(e.actorUserId());
    if (role != null) {
      sb.append(" · ").append(role);
    }
    return sb.toString();
  }

  private String primaryRole(UUID actorUserId) {
    if (actorUserId == null) return null;
    try {
      List<RoleName> roles = permissionResolver.roles(actorUserId);
      return roles.isEmpty() ? null : roles.get(0).name();
    } catch (RuntimeException ex) {
      return null; // papel é decorativo; nunca quebra o aviso
    }
  }

  private static String mapEnv(String profile) {
    if (profile == null) return "DEV";
    return switch (profile.toLowerCase()) {
      case "prod" -> "PRD";
      case "hml" -> "HML";
      default -> profile.toUpperCase();
    };
  }
}
