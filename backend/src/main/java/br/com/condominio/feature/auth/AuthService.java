package br.com.condominio.feature.auth;

import br.com.condominio.feature.auth.dto.AuthenticatedUserView;
import br.com.condominio.feature.role.PermissionResolver;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import br.com.condominio.shared.security.JwtService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserEmailRepository emailRepo;
  private final UserRepository userRepo;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final LoginAttemptTracker attemptTracker;
  private final PermissionResolver permissionResolver;

  @Transactional
  public LoginResult login(String email, String password) {
    try {
      log.info("[diag] login.start email={}", email);
      UserEmail userEmail = emailRepo.findActiveByEmailIgnoreCase(email).orElse(null);
      log.info("[diag] login.email-lookup found={}", userEmail != null);
      if (userEmail == null) {
        throw new BadCredentialsException("Invalid credentials");
      }
      User user = userRepo.findById(userEmail.getUserId()).orElse(null);
      log.info("[diag] login.user-lookup userId={} found={}", userEmail.getUserId(), user != null);
      if (user == null) {
        throw new BadCredentialsException("Invalid credentials");
      }
      boolean locked = attemptTracker.isLocked(user.getId());
      log.info("[diag] login.locked={}", locked);
      if (locked) {
        throw new BadCredentialsException("Account temporarily locked");
      }
      log.info("[diag] login.status={}", user.getStatus());
      if (user.getStatus() != UserStatus.ACTIVE) {
        attemptTracker.recordFailure(user.getId());
        throw new BadCredentialsException("Account not active");
      }
      String storedHash = user.getPasswordHash();
      log.info(
          "[diag] login.hash-info length={} pendingPlaceholder={}",
          storedHash == null ? -1 : storedHash.length(),
          "__PENDING__".equals(storedHash));
      boolean matches = passwordEncoder.matches(password, storedHash);
      log.info("[diag] login.matches={}", matches);
      if (!matches) {
        attemptTracker.recordFailure(user.getId());
        throw new BadCredentialsException("Invalid credentials");
      }
      attemptTracker.recordSuccess(user.getId());
      RefreshTokenService.IssuedToken issued = refreshTokenService.issueNew(user.getId());
      log.info("[diag] login.refresh-issued");
      LoginResult result = buildLoginResult(user, userEmail, issued);
      log.info("[diag] login.success");
      return result;
    } catch (BadCredentialsException e) {
      log.warn("[diag] login.bad-credentials reason={}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error(
          "[diag] login.unexpected-exception class={} msg={}",
          e.getClass().getName(),
          e.getMessage(),
          e);
      throw e;
    }
  }

  @Transactional
  public LoginResult refresh(String rawRefreshToken) {
    RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(rawRefreshToken);
    User user = userRepo.findById(rotated.userId()).orElseThrow();
    UserEmail primary = primaryEmail(user.getId());
    return buildLoginResult(user, primary, rotated);
  }

  public AuthenticatedUserView me(UUID userId) {
    User user = userRepo.findById(userId).orElseThrow();
    UserEmail primary = primaryEmail(userId);
    List<RoleName> roleNames = permissionResolver.roles(userId);
    return new AuthenticatedUserView(
        user.getId(),
        user.getFullName(),
        user.getGreetingName(),
        primary.getEmail(),
        user.getUnitId(),
        user.isUnitMaster(),
        roleNames.stream().map(Enum::name).toList(),
        permissionResolver.effectivePermissions(userId).stream().toList(),
        user.isMustChangePassword());
  }

  public void logout(UUID userId) {
    refreshTokenService.revokeAllForUser(userId, "logout");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Internal helpers
  // ──────────────────────────────────────────────────────────────────────────

  private LoginResult buildLoginResult(
      User user, UserEmail primaryEmail, RefreshTokenService.IssuedToken issued) {
    List<RoleName> roleNames = permissionResolver.roles(user.getId());
    List<String> roles = roleNames.stream().map(Enum::name).toList();
    List<String> authorities =
        permissionResolver.effectivePermissions(user.getId()).stream().toList();
    String accessToken =
        jwtService.signAccessToken(
            user.getId(), roles, authorities, user.getUnitId(), user.isUnitMaster());
    AuthenticatedUserView view =
        new AuthenticatedUserView(
            user.getId(),
            user.getFullName(),
            user.getGreetingName(),
            primaryEmail.getEmail(),
            user.getUnitId(),
            user.isUnitMaster(),
            roles,
            authorities,
            user.isMustChangePassword());
    return new LoginResult(accessToken, issued.rawToken(), view);
  }

  private UserEmail primaryEmail(UUID userId) {
    return emailRepo.findByUserId(userId).stream()
        .filter(UserEmail::isPrimary)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No primary email for userId=" + userId));
  }

  public record LoginResult(String accessToken, String refreshToken, AuthenticatedUserView user) {}
}
