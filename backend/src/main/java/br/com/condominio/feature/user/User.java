package br.com.condominio.feature.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "\"user\"")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id"})
@SQLDelete(sql = "UPDATE \"user\" SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

  @Id @GeneratedValue private UUID id;

  @Version private Long version;

  @Column(name = "unit_id")
  private UUID unitId;

  @Column(name = "is_unit_master", nullable = false)
  private boolean isUnitMaster;

  @Column(name = "full_name", nullable = false, length = 180)
  private String fullName;

  @Column(name = "greeting_name", length = 60)
  private String greetingName;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "phone_verified_at")
  private Instant phoneVerifiedAt;

  @Column(name = "gender", length = 20)
  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "password_pepper_version", nullable = false)
  private short passwordPepperVersion;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "status", nullable = false, length = 30)
  @Enumerated(EnumType.STRING)
  private UserStatus status = UserStatus.PENDING_APPROVAL;

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

  @Column(name = "anonymized_at")
  private Instant anonymizedAt;

  @Column(name = "consent_document_version", length = 20)
  private String consentDocumentVersion;

  @Column(name = "consent_accepted_at")
  private Instant consentAcceptedAt;

  @Column(name = "consent_accepted_ip", columnDefinition = "inet")
  private String consentAcceptedIp;

  @Column(name = "whatsapp_opt_in", nullable = false)
  private boolean whatsappOptIn;

  @Column(name = "whatsapp_opt_in_at")
  private Instant whatsappOptInAt;

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

  /** Trocar senha (já hash gerado externamente). Limpa flag must_change_password. */
  public void changePassword(String newHash, short pepperVersion) {
    this.passwordHash = newHash;
    this.passwordPepperVersion = pepperVersion;
    this.mustChangePassword = false;
  }

  /** Disable (soft) preserva tudo. */
  public void disable() {
    this.status = UserStatus.DISABLED;
  }

  /** Aprovar master após verificação do comprovante. */
  public void approveAsMaster(UUID approverId) {
    if (this.status != UserStatus.PENDING_APPROVAL) {
      throw new IllegalStateException(
          "User not in PENDING_APPROVAL state (current=" + this.status + ")");
    }
    if (!this.isUnitMaster) {
      throw new IllegalStateException("Only masters can be approved via this method");
    }
    this.status = UserStatus.ACTIVE;
    this.approvedByUserId = approverId;
    this.approvedAt = Instant.now();
    this.proofVerifiedAt = Instant.now();
  }

  /** Rejeitar cadastro pendente. */
  public void reject(UUID approverId, String reason) {
    if (this.status != UserStatus.PENDING_APPROVAL) {
      throw new IllegalStateException(
          "User not in PENDING_APPROVAL state (current=" + this.status + ")");
    }
    this.status = UserStatus.REJECTED;
    this.approvedByUserId = approverId;
    this.rejectionReason = reason;
  }
}
