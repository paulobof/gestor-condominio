package br.com.condominio.feature.unit;

import br.com.condominio.feature.role.PermissionCode;
import br.com.condominio.feature.role.PermissionGrantService;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MinioProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posse de unidade (proprietário): abertura de claim PENDING, aprovação e rejeição. Fica atrás da
 * flag {@code app.feature.unitownership.enabled} via os controllers que o usam.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitOwnershipService {

  private final UnitOwnershipRepository ownershipRepo;
  private final UnitRepository unitRepo;
  private final UserRepository userRepo;
  private final PermissionGrantService permissionGrants;
  private final FileStorage storage;
  private final MinioProperties props;

  /**
   * Abre uma posse PENDING para o par usuário+unidade, com o comprovante anexado. Falha se a
   * unidade não existe, já tem master (posse APPROVED) ou já há claim aberto do mesmo par.
   */
  @Transactional
  public UUID openClaim(
      UUID userId, UUID unitId, String proofKey, String proofFilename, String proofContentType) {
    unitRepo
        .findById(unitId)
        .orElseThrow(() -> new UnitOwnershipException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    if (ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)) {
      throw new UnitOwnershipException(
          "UNIT_HAS_MASTER", "Esta unidade já possui um master ativo.");
    }

    if (ownershipRepo.existsByUserIdAndUnitIdAndStatusIn(
        userId, unitId, List.of(OwnershipStatus.PENDING, OwnershipStatus.APPROVED))) {
      throw new UnitOwnershipException(
          "DUPLICATE_CLAIM", "Já existe um pedido aberto para esta unidade.");
    }

    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, proofKey, proofFilename, proofContentType);
    UUID id = ownershipRepo.save(o).getId();
    log.info("Ownership claim opened: ownershipId={} unitId={}", id, unitId);
    return id;
  }

  /**
   * Aprova uma posse PENDING: marca APPROVED, atribui master à unidade e concede {@code
   * RESIDENT_MANAGE} (idempotente). Se for a 1ª posse aprovada do usuário e ele ainda estiver
   * PENDING_APPROVAL, ativa a conta.
   */
  @Transactional
  public void approve(UUID ownershipId, UUID approverId) {
    UnitOwnership o =
        ownershipRepo
            .findByIdAndStatus(ownershipId, OwnershipStatus.PENDING)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));

    boolean firstApproved = ownershipRepo.findApprovedUnitIdsByUser(o.getUserId()).isEmpty();
    o.approve(approverId);

    Unit unit =
        unitRepo
            .findById(o.getUnitId())
            .orElseThrow(
                () -> new UnitOwnershipException("UNIT_NOT_FOUND", "Unidade não encontrada."));
    unit.assignMaster(o.getUserId());

    User user =
        userRepo
            .findById(o.getUserId())
            .orElseThrow(
                () -> new UnitOwnershipException("USER_NOT_FOUND", "Usuário não encontrado."));
    if (firstApproved && user.getStatus() == UserStatus.PENDING_APPROVAL) {
      user.approveAsMaster(approverId);
    }

    permissionGrants.grantIfAbsent(o.getUserId(), PermissionCode.RESIDENT_MANAGE, approverId);
    log.info("Ownership approved: ownershipId={} by approverId={}", ownershipId, approverId);
  }

  /** Rejeita uma posse PENDING, registra o motivo e purga o comprovante do storage. */
  @Transactional
  public void reject(UUID ownershipId, UUID approverId, String reason) {
    UnitOwnership o =
        ownershipRepo
            .findByIdAndStatus(ownershipId, OwnershipStatus.PENDING)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));
    o.reject(approverId, reason);
    if (o.getResidenceProofObjectKey() != null) {
      try {
        storage.delete(props.getBucketProofs(), o.getResidenceProofObjectKey());
      } catch (Exception e) {
        log.warn(
            "Failed to delete proof for rejected ownership {}: {}", ownershipId, e.getMessage());
      }
    }
    log.info("Ownership rejected: ownershipId={} by approverId={}", ownershipId, approverId);
  }
}
