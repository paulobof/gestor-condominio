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
    UserEmail userEmail = emailRepo.findActiveByEmailIgnoreCase(email).orElse(null);
    if (userEmail == null) {
      log.info("Login failure for email={} (not found)", email);
      throw new BadCredentialsException("Invalid credentials");
    }
    User user = userRepo.findById(userEmail.getUserId()).orElse(null);
    if (user == null) {
      throw new BadCredentialsException("Invalid credentials");
    }
    if (attemptTracker.isLocked(user.getId())) {
      log.warn("Login locked for userId={}", user.getId());
      throw new BadCredentialsException("Account temporarily locked");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      log.info("Login rejected for userId={} status={}", user.getId(), user.getStatus());
      attemptTracker.recordFailure(user.getId());
      throw new BadCredentialsException("Account not active");
    }
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      attemptTracker.recordFailure(user.getId());
      throw new BadCredentialsException("Invalid credentials");
    }
    attemptTracker.recordSuccess(user.getId());
    RefreshTokenService.IssuedToken issued = refreshTokenService.issueNew(user.getId());
    return buildLoginResult(user, userEmail, issued);
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
