package br.com.condominio.feature.password;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

/**
 * Token de reset de senha via WhatsApp. Per spec 4.4: gerado no request-reset, hash SHA-256
 * armazenado aqui, token claro vai pro WhatsApp e expira em 30 min (default
 * APP_PASSWORD_RESET_TTL).
 *
 * <p>Sem soft delete: histórico imutável; retenção via job (purga used/expired após 30 dias).
 */
@Entity
@Table(name = "password_reset_token")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id"})
public class PasswordResetToken {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_ip", columnDefinition = "text")
  private String createdIp;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  /** Cria token novo. Espera o tokenHash já calculado (SHA-256 do raw URL-safe). */
  public static PasswordResetToken create(
      UUID userId, String tokenHash, Instant expiresAt, String createdIp) {
    PasswordResetToken t = new PasswordResetToken();
    t.userId = userId;
    t.tokenHash = tokenHash;
    t.expiresAt = expiresAt;
    t.createdIp = createdIp;
    return t;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  /** Marca token como consumido. Idempotente: se já consumido, lança IllegalStateException. */
  public void consume(Instant now) {
    if (usedAt != null) {
      throw new IllegalStateException("Token já consumido em " + usedAt);
    }
    if (!isUsable(now)) {
      throw new IllegalStateException("Token expirado ou inválido");
    }
    this.usedAt = now;
  }

  /** Marca como entregue pelo bot WhatsApp. Idempotente — só registra a primeira entrega. */
  public void markDelivered(Instant now) {
    if (deliveredAt == null) {
      this.deliveredAt = now;
    }
  }
}
