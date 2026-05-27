package br.com.condominio.feature.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "sensitive_access_log")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SensitiveAccessLog {
  @Id @GeneratedValue private UUID id;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "target_user_id")
  private UUID targetUserId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "acted_at", nullable = false)
  private Instant actedAt;

  @Column(name = "client_ip", columnDefinition = "text")
  private String clientIp;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "request_id", length = 40)
  private String requestId;
}
