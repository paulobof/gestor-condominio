package br.com.condominio.feature.unit;

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
}
