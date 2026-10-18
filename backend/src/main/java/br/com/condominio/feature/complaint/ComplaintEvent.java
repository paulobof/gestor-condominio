package br.com.condominio.feature.complaint;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Histórico imutável de um chamado (sem soft delete, como os demais logs). */
@Entity
@Table(name = "complaint_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "kind"})
public class ComplaintEvent {

  @Id @GeneratedValue private UUID id;

  @Column(name = "complaint_id", nullable = false, updatable = false)
  private UUID complaintId;

  @Column(name = "actor_user_id", nullable = false, updatable = false)
  private UUID actorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private ComplaintEventKind kind;

  @Column(name = "from_value", length = 500, updatable = false)
  private String fromValue;

  @Column(name = "to_value", length = 500, updatable = false)
  private String toValue;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public static ComplaintEvent of(UUID complaintId, ComplaintChange ch) {
    ComplaintEvent e = new ComplaintEvent();
    e.complaintId = complaintId;
    e.actorUserId = ch.actorId();
    e.kind = ch.kind();
    e.fromValue = ch.fromValue();
    e.toValue = ch.toValue();
    return e;
  }
}
