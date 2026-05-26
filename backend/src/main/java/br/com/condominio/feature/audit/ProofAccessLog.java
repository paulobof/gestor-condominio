package br.com.condominio.feature.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "proof_access_log")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProofAccessLog {
  @Id @GeneratedValue private UUID id;

  @Column(name = "admin_user_id", nullable = false)
  private UUID adminUserId;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "accessed_at", nullable = false)
  private Instant accessedAt;

  @Column(name = "ip", columnDefinition = "inet")
  private String ip;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "presigned_url_ttl_seconds")
  private Integer presignedUrlTtlSeconds;

  @Column(name = "request_id", length = 40)
  private String requestId;
}
