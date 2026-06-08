package br.com.condominio.feature.password;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.password.event.PasswordResetCompletedEvent;
import br.com.condominio.feature.password.event.PasswordResetRequestedEvent;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordena o ciclo de reset de senha via WhatsApp.
 *
 * <p><b>requestReset:</b> sempre silencioso (não vaza existência). Se aplicável, gera token, salva
 * hash, publica {@link PasswordResetRequestedEvent}.
 *
 * <p><b>consumeReset:</b> valida hash + expiração + não-usado, troca a senha do usuário, registra
 * em {@link PasswordHistory} (anti-reuso das últimas 5), revoga refresh tokens, publica {@link
 * PasswordResetCompletedEvent}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

  private static final SecureRandom RNG = new SecureRandom();
  private static final int RAW_TOKEN_BYTES = 32;
  private static final int HISTORY_WINDOW = 5;

  private final UserRepository userRepo;
  private final UserEmailRepository userEmailRepo;
  private final PasswordResetTokenRepository tokenRepo;
  private final PasswordHistoryRepository historyRepo;
  private final RefreshTokenRepository refreshTokenRepo;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetProperties props;
  private final ApplicationEventPublisher events;

  @Value("${app.security.password.pepper-version:1}")
  private short pepperVersion;

  /**
   * Solicita reset de senha. Sempre retorna sem propagar erro mesmo se o usuário não existe ou não
   * é elegível — o controller responde 202 incondicionalmente.
   */
  @Transactional
  public void requestReset(String email, String clientIp) {
    Optional<User> userOpt = findEligibleUser(email);
    if (userOpt.isEmpty()) {
      log.info("password.reset.requested ignored (user not eligible)");
      return;
    }
    User user = userOpt.get();
    Instant now = Instant.now();

    // Invalida tokens anteriores (idempotência: se houver, garante que só um esteja ativo).
    tokenRepo.invalidateAllUserTokens(user.getId(), now);

    String rawToken = generateRawToken();
    String tokenHash = sha256Hex(rawToken);
    PasswordResetToken token =
        PasswordResetToken.create(user.getId(), tokenHash, now.plus(props.getTtl()), clientIp);
    token = tokenRepo.save(token);

    events.publishEvent(
        new PasswordResetRequestedEvent(
            user.getId(),
            token.getId(),
            rawToken,
            user.getPhone(),
            user.getGreetingName(),
            props.getTtl().toMinutes()));
    log.info("password.reset.requested userId={}", user.getId());
  }

  @Transactional
  public void consumeReset(String rawToken, String newPassword, String clientIp) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new PasswordResetException("INVALID_OR_EXPIRED_TOKEN", "Token inválido ou expirado.");
    }
    String hash = sha256Hex(rawToken);
    PasswordResetToken token =
        tokenRepo
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new PasswordResetException(
                        "INVALID_OR_EXPIRED_TOKEN", "Token inválido ou expirado."));
    Instant now = Instant.now();
    if (!token.isUsable(now)) {
      throw new PasswordResetException("INVALID_OR_EXPIRED_TOKEN", "Token inválido ou expirado.");
    }
    User user =
        userRepo
            .findById(token.getUserId())
            .orElseThrow(
                () ->
                    new PasswordResetException(
                        "INVALID_OR_EXPIRED_TOKEN", "Token inválido ou expirado."));

    // Checa reuso: nova senha não pode bater com nenhuma das últimas N.
    List<PasswordHistory> recent = historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(user.getId());
    for (PasswordHistory h : recent) {
      if (passwordEncoder.matches(newPassword, h.getPasswordHash())) {
        throw new PasswordResetException(
            "PASSWORD_REUSED", "Você já usou essa senha recentemente.");
      }
    }

    String newHash = passwordEncoder.encode(newPassword);
    user.changePassword(newHash, pepperVersion);
    userRepo.save(user);
    historyRepo.save(PasswordHistory.create(user.getId(), newHash, pepperVersion));

    token.consume(now);
    tokenRepo.save(token);

    refreshTokenRepo.revokeAllByUserId(user.getId(), "password_reset");

    events.publishEvent(
        new PasswordResetCompletedEvent(user.getId(), user.getPhone(), user.getGreetingName()));
    log.info("password.reset.consumed userId={} ip={}", user.getId(), clientIp);
  }

  // ============ helpers package-private para testes ============

  Optional<User> findEligibleUser(String email) {
    if (email == null || email.isBlank()) return Optional.empty();
    return userEmailRepo
        .findActiveByEmailIgnoreCase(email)
        .flatMap(ue -> userRepo.findById(ue.getUserId()))
        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .filter(u -> u.getPhoneVerifiedAt() != null);
  }

  static String generateRawToken() {
    byte[] buf = new byte[RAW_TOKEN_BYTES];
    RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  static String sha256Hex(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível?!", e);
    }
  }

  @Configuration
  @EnableConfigurationProperties(PasswordResetProperties.class)
  static class PropertiesConfig {}
}
