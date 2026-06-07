package br.com.condominio.feature.unit;

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
@Table(name = "unit")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "code"})
@SQLDelete(sql = "UPDATE unit SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Unit {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "tower", nullable = false, length = 1)
  private String tower;

  @Column(name = "floor", nullable = false)
  private short floor;

  @Column(name = "position", nullable = false)
  private short position;

  @Column(name = "code", nullable = false, length = 8)
  private String code;

  @Column(name = "master_user_id")
  private UUID masterUserId;

  @Column(name = "created_at", updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at")
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Atribuir master à unidade. Falha se já há master ativo. */
  public void assignMaster(UUID userId) {
    if (this.masterUserId != null) {
      throw new IllegalStateException("Unit already has a master");
    }
    this.masterUserId = userId;
  }
}
