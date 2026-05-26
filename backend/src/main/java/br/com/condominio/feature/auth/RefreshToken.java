package br.com.condominio.feature.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "refresh_token")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id"})
@SQLDelete(sql = "UPDATE refresh_token SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RefreshToken {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "token_family", nullable = false)
  private UUID tokenFamily;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason", length = 80)
  private String revokedReason;

  @Column(name = "created_at", updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at")
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static RefreshToken create(UUID userId, String tokenHash, UUID family, Instant expiresAt) {
    RefreshToken t = new RefreshToken();
    t.userId = userId;
    t.tokenHash = tokenHash;
    t.tokenFamily = family;
    t.expiresAt = expiresAt;
    t.revoked = false;
    return t;
  }

  public void revoke(String reason) {
    this.revoked = true;
    this.revokedAt = Instant.now();
    this.revokedReason = reason;
  }
}
