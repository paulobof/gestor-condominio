package br.com.condominio.feature.unit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitOwnershipRepository extends JpaRepository<UnitOwnership, UUID> {

  /** Posses do usuário em um dado status (ex.: APPROVED = "minhas unidades"). */
  List<UnitOwnership> findByUserIdAndStatus(UUID userId, OwnershipStatus status);

  /** Master atual de uma unidade (posse APPROVED). */
  Optional<UnitOwnership> findByUnitIdAndStatus(UUID unitId, OwnershipStatus status);

  /** Claims pendentes (para a tela admin), mais antigos primeiro. */
  Page<UnitOwnership> findByStatusOrderByCreatedAtAsc(OwnershipStatus status, Pageable pageable);
}
