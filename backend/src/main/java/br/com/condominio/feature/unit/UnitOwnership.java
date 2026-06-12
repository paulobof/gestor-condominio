package br.com.condominio.feature.unit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Posse de uma unidade por um usuário (proprietário). Fonte de verdade de quem é/pleiteia master.
 */
@Entity
@Table(name = "unit_ownership")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE unit_ownership SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class UnitOwnership {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "unit_id", nullable = false)
  private UUID unitId;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private OwnershipStatus status;

  @Column(name = "residence_proof_object_key")
  private String residenceProofObjectKey;

  @Column(name = "residence_proof_filename")
  private String residenceProofFilename;

  @Column(name = "residence_proof_content_type", length = 80)
  private String residenceProofContentType;

  @Column(name = "residence_proof_uploaded_at")
  private Instant residenceProofUploadedAt;

  @Column(name = "proof_verified_at")
  private Instant proofVerifiedAt;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejection_reason", columnDefinition = "text")
  private String rejectionReason;

  @Column(name = "created_at", updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at")
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "created_by_user_id", updatable = false)
  @CreatedBy
  private UUID createdByUserId;

  @Column(name = "updated_by_user_id")
  @LastModifiedBy
  private UUID updatedByUserId;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by_user_id")
  private UUID deletedByUserId;

  // ===== Métodos de domínio =====

  /** Cria uma posse PENDING com o comprovante anexado. */
  public static UnitOwnership pending(
      UUID userId,
      UUID unitId,
      String proofObjectKey,
      String proofFilename,
      String proofContentType) {
    UnitOwnership o = new UnitOwnership();
    o.userId = userId;
    o.unitId = unitId;
    o.status = OwnershipStatus.PENDING;
    o.residenceProofObjectKey = proofObjectKey;
    o.residenceProofFilename = proofFilename;
    o.residenceProofContentType = proofContentType;
    o.residenceProofUploadedAt = Instant.now();
    return o;
  }

  /** Aprova a posse (comprovante verificado). Só a partir de PENDING. */
  public void approve(UUID approverId) {
    if (this.status != OwnershipStatus.PENDING) {
      throw new IllegalStateException("Ownership not PENDING (current=" + this.status + ")");
    }
    Instant now = Instant.now();
    this.status = OwnershipStatus.APPROVED;
    this.approvedByUserId = approverId;
    this.approvedAt = now;
    this.proofVerifiedAt = now;
  }

  /** Rejeita a posse pendente, registrando o motivo. */
  public void reject(UUID approverId, String reason) {
    if (this.status != OwnershipStatus.PENDING) {
      throw new IllegalStateException("Ownership not PENDING (current=" + this.status + ")");
    }
    this.status = OwnershipStatus.REJECTED;
    this.approvedByUserId = approverId;
    this.rejectionReason = reason;
  }
}
