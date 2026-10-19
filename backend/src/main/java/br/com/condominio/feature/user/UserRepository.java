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

  /**
   * Fila de exceção do admin: todo cadastro de morador parado em PENDING_APPROVAL — o master da
   * unidade que não respondeu, ou o master pendente do fluxo antigo. Proprietários (sem unidade)
   * ficam de fora; eles têm fila própria em {@code /api/ownership-claims}.
   */
  @Query(
      "SELECT u FROM User u WHERE u.status = br.com.condominio.feature.user.UserStatus.PENDING_APPROVAL "
          + "AND u.unitId IS NOT NULL ORDER BY u.createdAt DESC")
  Page<User> findPendingResidents(Pageable pageable);

  /** Pedidos de acesso parados nas unidades informadas (visão do master). */
  List<User> findByUnitIdInAndStatusAndIsUnitMasterFalse(List<UUID> unitIds, UserStatus status);

  /**
   * Moradores de uma unidade: não-master, em qualquer status exceto o informado (ex.: ANONYMIZED).
   * Soft-deleted já é filtrado pelo {@code @SQLRestriction} da entidade {@code User}.
   */
  List<User> findByUnitIdAndStatusNotAndIsUnitMasterFalse(UUID unitId, UserStatus status);

  /**
   * Moradores de um conjunto de unidades (multi-unidade): não-master, exceto o status informado.
   */
  List<User> findByUnitIdInAndStatusNotAndIsUnitMasterFalse(
      java.util.Collection<UUID> unitIds, UserStatus status);

  @Query("SELECT u.unitId FROM User u WHERE u.id = :id")
  Optional<UUID> findUnitIdById(@Param("id") UUID id);

  /**
   * Contas sem uso desde {@code cutoff} (último acesso, ou criação se nunca entrou) que ainda não
   * foram anonimizadas. Só moradores comuns: quem tem qualquer papel além de RESIDENT (admin,
   * síndico, portaria, editores...) é conta institucional e fica de fora. Query nativa: filtra
   * {@code deleted_at} à mão.
   */
  @Query(
      value =
          "SELECT u.id FROM \"user\" u "
              + "WHERE u.deleted_at IS NULL AND u.status <> 'ANONYMIZED' "
              + "AND COALESCE(u.last_login_at, u.created_at) < :cutoff "
              + "AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id "
              + "                WHERE ur.user_id = u.id AND r.name <> 'RESIDENT')",
      nativeQuery = true)
  List<UUID> findInactiveAnonymizationCandidates(@Param("cutoff") Instant cutoff);
}
