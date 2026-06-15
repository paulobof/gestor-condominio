package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitOwnershipServiceTest {

  @Mock private UnitOwnershipRepository ownershipRepo;
  @Mock private UnitRepository unitRepo;
  @InjectMocks private UnitOwnershipService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID unitId = UUID.randomUUID();

  @Test
  void openClaim_createsPendingOwnership_withProofMetadata() {
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(mock(Unit.class)));
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(false);
    when(ownershipRepo.existsByUserIdAndUnitIdAndStatusIn(eq(userId), eq(unitId), any()))
        .thenReturn(false);
    when(ownershipRepo.save(any(UnitOwnership.class))).thenAnswer(inv -> inv.getArgument(0));

    service.openClaim(userId, unitId, "proofs/k1", "comprovante.pdf", "application/pdf");

    ArgumentCaptor<UnitOwnership> captor = ArgumentCaptor.forClass(UnitOwnership.class);
    verify(ownershipRepo).save(captor.capture());
    UnitOwnership saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(OwnershipStatus.PENDING);
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getUnitId()).isEqualTo(unitId);
    assertThat(saved.getResidenceProofObjectKey()).isEqualTo("proofs/k1");
    assertThat(saved.getResidenceProofFilename()).isEqualTo("comprovante.pdf");
    assertThat(saved.getResidenceProofContentType()).isEqualTo("application/pdf");
  }

  @Test
  void openClaim_rejectsWhenUnitHasMaster() {
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(mock(Unit.class)));
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(true);

    assertThatThrownBy(
            () -> service.openClaim(userId, unitId, "proofs/k1", "c.pdf", "application/pdf"))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e -> assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("UNIT_HAS_MASTER"));
    verify(ownershipRepo, never()).save(any());
  }

  @Test
  void openClaim_rejectsDuplicateOpenClaim() {
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(mock(Unit.class)));
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(false);
    when(ownershipRepo.existsByUserIdAndUnitIdAndStatusIn(
            eq(userId), eq(unitId), any(Collection.class)))
        .thenReturn(true);

    assertThatThrownBy(
            () -> service.openClaim(userId, unitId, "proofs/k1", "c.pdf", "application/pdf"))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e -> assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("DUPLICATE_CLAIM"));
    verify(ownershipRepo, never()).save(any());
  }

  @Test
  void openClaim_rejectsWhenUnitNotFound() {
    when(unitRepo.findById(unitId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.openClaim(userId, unitId, "proofs/k1", "c.pdf", "application/pdf"))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e -> assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("UNIT_NOT_FOUND"));
  }
}
