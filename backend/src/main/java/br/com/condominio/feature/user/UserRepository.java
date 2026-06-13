package br.com.condominio.feature.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByIdAndStatus(UUID id, UserStatus status);

  @Query(
      "SELECT u FROM User u WHERE u.status = br.com.condominio.feature.user.UserStatus.PENDING_APPROVAL "
          + "AND u.isUnitMaster = true ORDER BY u.createdAt DESC")
  Page<User> findPendingMasters(Pageable pageable);

  /**
   * Users ACTIVE com comprovante carregado antes de {@code cutoff} — alvo do {@code
   * ProofRetentionScheduler} (purga após 180 dias da aprovação).
   */
  @Query(
      "SELECT u FROM User u "
          + "WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE "
          + "  AND u.residenceProofObjectKey IS NOT NULL "
          + "  AND u.approvedAt < :cutoff")
  List<User> findApprovedWithProofBefore(@Param("cutoff") Instant cutoff);

  /**
   * Moradores de uma unidade: não-master, em qualquer status exceto o informado (ex.: ANONYMIZED).
   * Soft-deleted já é filtrado pelo {@code @SQLRestriction} da entidade {@code User}.
   */
  List<User> findByUnitIdAndStatusNotAndIsUnitMasterFalse(UUID unitId, UserStatus status);
}
