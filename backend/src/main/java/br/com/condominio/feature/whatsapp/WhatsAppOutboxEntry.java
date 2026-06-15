package br.com.condominio.feature.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Outbox de mensagens WhatsApp. Cada chamada {@code WhatsAppNotificationClient.send(...)} grava uma
 * entrada PENDING; após resposta do bot vira SENT ou FAILED. Job @Scheduled reprocessa FAILED até
 * max_attempts.
 *
 * <p>Soft delete habilitado pra job de retenção (90 dias) manter histórico até o cleanup.
 */
@Entity
@Table(name = "whatsapp_outbox")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "template", "status", "attempts"})
@SQLDelete(sql = "UPDATE whatsapp_outbox SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class WhatsAppOutboxEntry {

  public enum Status {
    PENDING,
    SENT,
    FAILED
  }

  @Id @GeneratedValue private UUID id;

  // Aceita telefone (DDI) ou JID de grupo (ex.: 1203...@g.us) para os avisos de atividade.
  @Column(name = "to_phone", nullable = false, length = 64)
  private String toPhone;

  @Column(name = "template", nullable = false, length = 60)
  @Enumerated(EnumType.STRING)
  private WhatsAppTemplate template;

  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String payload;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Status status;

  @Column(name = "attempts", nullable = false)
  private short attempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static WhatsAppOutboxEntry create(
      String toPhone, WhatsAppTemplate template, String payloadJson) {
    WhatsAppOutboxEntry e = new WhatsAppOutboxEntry();
    e.toPhone = toPhone;
    e.template = template;
    e.payload = payloadJson;
    e.status = Status.PENDING;
    e.attempts = 0;
    return e;
  }

  public void markSent(Instant now) {
    this.status = Status.SENT;
    this.sentAt = now;
    this.errorMessage = null;
  }

  public void markFailed(String error, Instant now) {
    this.status = Status.FAILED;
    this.lastAttemptAt = now;
    this.attempts = (short) (this.attempts + 1);
    this.errorMessage = truncate(error, 1000);
  }

  public void recordAttempt(Instant now) {
    this.lastAttemptAt = now;
    this.attempts = (short) (this.attempts + 1);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
