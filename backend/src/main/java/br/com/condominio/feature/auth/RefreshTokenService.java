package br.com.condominio.feature.auth;

import br.com.condominio.shared.security.JwtProperties;
import br.com.condominio.shared.time.Clock;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private final RefreshTokenRepository repo;
  private final JwtProperties jwtProps;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  /** Issue a new refresh token for the user (new family). */
  @Transactional
  public IssuedToken issueNew(UUID userId) {
    UUID family = UUID.randomUUID();
    return issueIntoFamily(userId, family);
  }

  /** Rotate the given token, returning a new one. Detects replay → revoke family. */
  @Transactional
  public IssuedToken rotate(String rawToken) {
    String hash = sha256(rawToken);
    RefreshToken existing =
        repo.findByTokenHash(hash)
            .orElseThrow(() -> new SecurityException("Unknown refresh token"));
    if (existing.getExpiresAt().isBefore(clock.now())) {
      throw new SecurityException("Refresh token expired");
    }
    int revoked = repo.revokeIfActive(existing.getId(), "rotation");
    if (revoked == 0) {
      // Token already used → replay attack. Revoke entire family.
      int familyCount = repo.revokeFamily(existing.getTokenFamily(), "replay-detected");
      log.warn(
          "Refresh token replay detected for user {}, revoked {} tokens in family {}",
          existing.getUserId(),
          familyCount,
          existing.getTokenFamily());
      throw new SecurityException("Refresh token replay detected");
    }
    return issueIntoFamily(existing.getUserId(), existing.getTokenFamily());
  }

  @Transactional
  public void revokeAllForUser(UUID userId, String reason) {
    repo.findAll().stream()
        .filter(t -> t.getUserId().equals(userId) && !t.isRevoked())
        .forEach(t -> repo.revokeIfActive(t.getId(), reason));
  }

  private IssuedToken issueIntoFamily(UUID userId, UUID family) {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String hash = sha256(rawToken);
    Instant expiresAt = clock.now().plus(jwtProps.getRefreshTtl());
    RefreshToken entity = RefreshToken.create(userId, hash, family, expiresAt);
    repo.save(entity);
    return new IssuedToken(userId, rawToken, expiresAt);
  }

  static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record IssuedToken(UUID userId, String rawToken, Instant expiresAt) {}
}
