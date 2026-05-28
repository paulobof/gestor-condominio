package br.com.condominio.feature.password;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Invalida (marca usedAt=now) todos os tokens não consumidos do usuário. */
  @Modifying
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now "
          + "WHERE t.userId = :userId AND t.usedAt IS NULL")
  int invalidateAllUserTokens(@Param("userId") UUID userId, @Param("now") Instant now);

  /** Marca delivered_at se ainda não foi marcado. Usado pelo listener async após bot enviar. */
  @Modifying
  @Query(
      "UPDATE PasswordResetToken t SET t.deliveredAt = :now "
          + "WHERE t.id = :id AND t.deliveredAt IS NULL")
  int markDelivered(@Param("id") UUID id, @Param("now") Instant now);

  /** Purga tokens consumidos ou expirados antes de cutoff (job de retenção). */
  @Modifying
  @Query(
      "DELETE FROM PasswordResetToken t "
          + "WHERE (t.usedAt IS NOT NULL AND t.usedAt < :cutoff) "
          + "   OR (t.expiresAt < :cutoff)")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
