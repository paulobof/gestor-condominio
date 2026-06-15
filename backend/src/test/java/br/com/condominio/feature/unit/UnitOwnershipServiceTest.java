package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.role.PermissionGrantService;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UnitOwnershipServiceTest {

  @Mock private UnitOwnershipRepository ownershipRepo;
  @Mock private UnitRepository unitRepo;
  @Mock private UserRepository userRepo;
  @Mock private PermissionGrantService permissionGrants;
  @Mock private RoleRepository roleRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private FileStorage storage;
  @Mock private MagicBytesValidator magicBytes;
  @Mock private MinioProperties props;
  @InjectMocks private UnitOwnershipService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID unitId = UUID.randomUUID();

  @Test
  void hasApprovedOwner_returnsTrueWhenApprovedExists() {
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(true);
    assertThat(service.hasApprovedOwner(unitId)).isTrue();
  }

  @Test
  void hasApprovedOwner_returnsFalseWhenNoneApproved() {
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(false);
    assertThat(service.hasApprovedOwner(unitId)).isFalse();
  }

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
  void openClaim_rejectsWhenUnitHasOwner() {
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(mock(Unit.class)));
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(true);

    assertThatThrownBy(
            () -> service.openClaim(userId, unitId, "proofs/k1", "c.pdf", "application/pdf"))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e -> assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("UNIT_HAS_OWNER"));
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

  @Test
  void approve_grantsProprietarioRole_andActivates_withoutMastership() {
    UUID ownershipId = UUID.randomUUID();
    UUID approver = UUID.randomUUID();

    UnitOwnership claim = UnitOwnership.pending(userId, unitId, "key", "f.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(claim));
    when(ownershipRepo.findApprovedUnitIdsByUser(userId)).thenReturn(List.of()); // 1ª posse

    Role proprietario = mock(Role.class);
    when(proprietario.getId()).thenReturn((short) 9);
    when(roleRepo.findByName(RoleName.PROPRIETARIO)).thenReturn(Optional.of(proprietario));
    when(userRoleRepo.existsById(any())).thenReturn(false);

    User user = mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.PENDING_APPROVAL);
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));

    service.approve(ownershipId, approver);

    assertThat(claim.getStatus()).isEqualTo(OwnershipStatus.APPROVED);
    verify(userRoleRepo).save(any(UserRole.class)); // papel concedido
    verify(user).approveAsOwner(approver); // ativa sem mastership
    verify(unitRepo, never()).findById(unitId); // NÃO resolve unidade p/ assignMaster
    verifyNoInteractions(permissionGrants); // NÃO concede RESIDENT_MANAGE
  }

  @Test
  void approve_secondOwnership_grantsRole_doesNotReactivateUser() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, "proofs/k2", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(o));
    when(ownershipRepo.findApprovedUnitIdsByUser(userId)).thenReturn(List.of(UUID.randomUUID()));

    Role proprietario = mock(Role.class);
    when(proprietario.getId()).thenReturn((short) 9);
    when(roleRepo.findByName(RoleName.PROPRIETARIO)).thenReturn(Optional.of(proprietario));
    when(userRoleRepo.existsById(any())).thenReturn(false);

    service.approve(ownershipId, approverId);

    verify(userRoleRepo).save(any(UserRole.class));
    verify(userRepo, never()).findById(any()); // não precisa ativar usuário
    verifyNoInteractions(permissionGrants);
  }

  @Test
  void approve_idempotent_doesNotSaveRoleWhenAlreadyGranted() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership claim =
        UnitOwnership.pending(userId, unitId, "proofs/k3", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(claim));
    when(ownershipRepo.findApprovedUnitIdsByUser(userId)).thenReturn(List.of(UUID.randomUUID()));

    Role proprietario = mock(Role.class);
    when(proprietario.getId()).thenReturn((short) 9);
    when(roleRepo.findByName(RoleName.PROPRIETARIO)).thenReturn(Optional.of(proprietario));
    when(userRoleRepo.existsById(any())).thenReturn(true); // role já concedida

    service.approve(ownershipId, approverId);

    verify(userRoleRepo, never()).save(any()); // não deve salvar novamente
  }

  @Test
  void approve_activeUser_grantsRole_butDoesNotCallApproveAsOwner() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership claim =
        UnitOwnership.pending(userId, unitId, "proofs/k4", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(claim));
    when(ownershipRepo.findApprovedUnitIdsByUser(userId)).thenReturn(List.of()); // 1ª posse

    Role proprietario = mock(Role.class);
    when(proprietario.getId()).thenReturn((short) 9);
    when(roleRepo.findByName(RoleName.PROPRIETARIO)).thenReturn(Optional.of(proprietario));
    when(userRoleRepo.existsById(any())).thenReturn(false);

    User user = mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.ACTIVE); // usuário já ativo
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));

    service.approve(ownershipId, approverId);

    verify(userRoleRepo).save(any(UserRole.class)); // papel concedido
    verify(user, never()).approveAsOwner(any()); // não chama approveAsOwner
  }

  @Test
  void reject_purgesProofFromStorage() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, "proofs/k1", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(o));
    when(props.getBucketProofs()).thenReturn("residence-proofs");

    service.reject(ownershipId, approverId, "comprovante ilegível");

    assertThat(o.getStatus()).isEqualTo(OwnershipStatus.REJECTED);
    verify(storage).delete("residence-proofs", "proofs/k1");
  }

  @Test
  void reject_pendingOwner_alsoRejectsUserAccount() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, "proofs/k1", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(o));
    when(props.getBucketProofs()).thenReturn("residence-proofs");

    User user = mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.PENDING_APPROVAL);
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));

    service.reject(ownershipId, approverId, "motivo");

    verify(user).reject(approverId, "motivo");
  }

  @Test
  void reject_activeUserExtraClaim_doesNotRejectUserAccount() {
    UUID ownershipId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, "proofs/k2", "c.pdf", "application/pdf");
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.of(o));
    when(props.getBucketProofs()).thenReturn("residence-proofs");

    User user = mock(User.class);
    when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));

    service.reject(ownershipId, approverId, "comprovante ilegível");

    verify(user, never()).reject(any(), any());
  }

  @Test
  void approve_rejectsWhenClaimNotFound() {
    UUID ownershipId = UUID.randomUUID();
    when(ownershipRepo.findByIdAndStatus(ownershipId, OwnershipStatus.PENDING))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.approve(ownershipId, UUID.randomUUID()))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e -> assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("CLAIM_NOT_FOUND"));
  }

  @Test
  void claimExtraUnit_rejectsInvalidProofType() {
    MockMultipartFile proof =
        new MockMultipartFile("proof", "f.zip", "application/zip", new byte[] {0x50, 0x4B});
    when(magicBytes.detect(any())).thenReturn("application/zip");
    when(magicBytes.isAcceptedForProof("application/zip")).thenReturn(false);

    assertThatThrownBy(() -> service.claimExtraUnit(userId, "702C", proof))
        .isInstanceOf(UnitOwnershipException.class)
        .satisfies(
            e ->
                assertThat(((UnitOwnershipException) e).getCode()).isEqualTo("PROOF_TYPE_INVALID"));
    verify(ownershipRepo, never()).save(any());
  }

  @Test
  void claimExtraUnit_uploadsAndOpensClaim() {
    MockMultipartFile proof =
        new MockMultipartFile(
            "proof", "comprovante.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForProof("application/pdf")).thenReturn(true);
    Unit unit = mock(Unit.class);
    when(unit.getId()).thenReturn(unitId);
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(unit));
    when(props.getBucketProofs()).thenReturn("residence-proofs");
    when(storage.upload(eq("residence-proofs"), any(), anyLong(), eq("application/pdf")))
        .thenReturn("objkey");
    when(ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)).thenReturn(false);
    when(ownershipRepo.existsByUserIdAndUnitIdAndStatusIn(eq(userId), eq(unitId), any()))
        .thenReturn(false);
    when(ownershipRepo.save(any(UnitOwnership.class))).thenAnswer(inv -> inv.getArgument(0));

    service.claimExtraUnit(userId, "702C", proof);

    verify(storage).upload(eq("residence-proofs"), any(), anyLong(), eq("application/pdf"));
    ArgumentCaptor<UnitOwnership> captor = ArgumentCaptor.forClass(UnitOwnership.class);
    verify(ownershipRepo).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(OwnershipStatus.PENDING);
    assertThat(captor.getValue().getResidenceProofObjectKey()).isEqualTo("objkey");
  }
}
