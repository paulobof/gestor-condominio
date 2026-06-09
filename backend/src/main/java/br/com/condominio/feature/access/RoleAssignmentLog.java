package br.com.condominio.feature.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Log imutável (append-only) de atribuição/remoção de roles. Sem soft delete: é trilha de
 * auditoria, como {@code sensitive_access_log}. Criado só via factories {@link #assign}/{@link
 * #remove}.
 */
@Entity
@Table(name = "role_assignment_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "action"})
public class RoleAssignmentLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "action", nullable = false, length = 10)
  private String action;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "role_id", nullable = false)
  private Short roleId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  private RoleAssignmentLog(String action, UUID targetUserId, Short roleId, UUID actorUserId) {
    this.action = action;
    this.targetUserId = targetUserId;
    this.roleId = roleId;
    this.actorUserId = actorUserId;
    this.createdAt = Instant.now();
  }

  public static RoleAssignmentLog assign(UUID targetUserId, Short roleId, UUID actorUserId) {
    return new RoleAssignmentLog("ASSIGN", targetUserId, roleId, actorUserId);
  }

  public static RoleAssignmentLog remove(UUID targetUserId, Short roleId, UUID actorUserId) {
    return new RoleAssignmentLog("REMOVE", targetUserId, roleId, actorUserId);
  }
}
