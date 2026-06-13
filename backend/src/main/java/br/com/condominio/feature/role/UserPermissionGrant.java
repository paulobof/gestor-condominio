package br.com.condominio.feature.role;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "user_permission_grant")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class UserPermissionGrant {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "permission_id", nullable = false)
  private Short permissionId;

  @Column(name = "granted_by_user_id")
  private UUID grantedByUserId;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt = Instant.now();

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by_user_id")
  private UUID revokedByUserId;

  public UserPermissionGrant(UUID userId, Short permissionId, UUID grantedByUserId) {
    this.userId = userId;
    this.permissionId = permissionId;
    this.grantedByUserId = grantedByUserId;
  }

  public boolean isActive() {
    return revokedAt == null;
  }
}
