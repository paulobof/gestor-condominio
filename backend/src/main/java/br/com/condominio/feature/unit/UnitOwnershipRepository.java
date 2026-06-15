package br.com.condominio.feature.unit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitOwnershipRepository extends JpaRepository<UnitOwnership, UUID> {

  /** Posses do usuário em um dado status (ex.: APPROVED = "minhas unidades"). */
  List<UnitOwnership> findByUserIdAndStatus(UUID userId, OwnershipStatus status);

  /** Master atual de uma unidade (posse APPROVED). */
  Optional<UnitOwnership> findByUnitIdAndStatus(UUID unitId, OwnershipStatus status);

  /** Claims pendentes (para a tela admin), mais antigos primeiro. */
  Page<UnitOwnership> findByStatusOrderByCreatedAtAsc(OwnershipStatus status, Pageable pageable);

  /** Há posse nesse status na unidade? (ex.: APPROVED = unidade já tem master). */
  boolean existsByUnitIdAndStatus(UUID unitId, OwnershipStatus status);

  /** Claim por id num dado status (ex.: PENDING para aprovar/rejeitar). */
  Optional<UnitOwnership> findByIdAndStatus(UUID id, OwnershipStatus status);

  /** Há claim aberto (ex.: PENDING/APPROVED) do par usuário+unidade? Evita duplicidade. */
  boolean existsByUserIdAndUnitIdAndStatusIn(
      UUID userId, UUID unitId, java.util.Collection<OwnershipStatus> statuses);

  /** {@code unit_id} das posses do usuário num status — base de "minhas unidades". */
  @Query("SELECT o.unitId FROM UnitOwnership o WHERE o.userId = :userId AND o.status = :status")
  List<UUID> findUnitIdsByUserAndStatus(
      @Param("userId") UUID userId, @Param("status") OwnershipStatus status);

  /** Conveniência: unidades onde o usuário é master (posse APPROVED). */
  default List<UUID> findApprovedUnitIdsByUser(UUID userId) {
    return findUnitIdsByUserAndStatus(userId, OwnershipStatus.APPROVED);
  }
}
