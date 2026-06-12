package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnitOwnershipTest {

  @Test
  void pending_setsClaimFieldsAndStatus() {
    UUID user = UUID.randomUUID();
    UUID unit = UUID.randomUUID();

    UnitOwnership o =
        UnitOwnership.pending(user, unit, "key/abc", "comprovante.pdf", "application/pdf");

    assertThat(o.getUserId()).isEqualTo(user);
    assertThat(o.getUnitId()).isEqualTo(unit);
    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.PENDING);
    assertThat(o.getResidenceProofObjectKey()).isEqualTo("key/abc");
    assertThat(o.getResidenceProofFilename()).isEqualTo("comprovante.pdf");
    assertThat(o.getResidenceProofContentType()).isEqualTo("application/pdf");
    assertThat(o.getResidenceProofUploadedAt()).isNotNull();
  }

  @Test
  void approve_pending_marksApprovedAndStampsApprover() {
    UUID approver = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");

    o.approve(approver);

    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.APPROVED);
    assertThat(o.getApprovedByUserId()).isEqualTo(approver);
    assertThat(o.getApprovedAt()).isNotNull();
    assertThat(o.getProofVerifiedAt()).isNotNull();
  }

  @Test
  void approve_notPending_throws() {
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");
    o.approve(UUID.randomUUID());

    assertThatThrownBy(() -> o.approve(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reject_pending_marksRejectedWithReason() {
    UUID approver = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(UUID.randomUUID(), UUID.randomUUID(), "k", "f", "application/pdf");

    o.reject(approver, "comprovante ilegível");

    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.REJECTED);
    assertThat(o.getApprovedByUserId()).isEqualTo(approver);
    assertThat(o.getRejectionReason()).isEqualTo("comprovante ilegível");
  }
}
