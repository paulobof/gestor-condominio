package br.com.condominio.feature.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      value =
          "UPDATE refresh_token SET revoked = true, revoked_at = now(), revoked_reason = :reason "
              + "WHERE id = :id AND revoked = false",
      nativeQuery = true)
  int revokeIfActive(@Param("id") UUID id, @Param("reason") String reason);

  @Modifying
  @Query(
      value =
          "UPDATE refresh_token SET revoked = true, revoked_at = now(), revoked_reason = :reason "
              + "WHERE token_family = :family AND revoked = false",
      nativeQuery = true)
  int revokeFamily(@Param("family") UUID family, @Param("reason") String reason);
}
