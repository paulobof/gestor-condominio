package br.com.condominio.feature.auth;

import br.com.condominio.feature.auth.dto.AuthenticatedUserView;
import br.com.condominio.feature.auth.dto.LoginRequest;
import br.com.condominio.feature.auth.dto.LoginResponse;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Value("${app.security.cookie.domain:}")
  private String cookieDomain;

  @Value("${app.security.cookie.secure:false}")
  private boolean cookieSecure;

  @Value("${app.security.jwt.refresh-ttl:P7D}")
  private String refreshTtlIso;

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthService.LoginResult result = authService.login(request.email(), request.password());
    addRefreshCookie(response, result.refreshToken());
    return ResponseEntity.ok(new LoginResponse(result.accessToken(), result.user()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(value = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new AccessDeniedException("Missing refresh token");
    }
    AuthService.LoginResult result = authService.refresh(refreshToken);
    addRefreshCookie(response, result.refreshToken());
    return ResponseEntity.ok(new LoginResponse(result.accessToken(), result.user()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal AuthenticatedUserPrincipal principal, HttpServletResponse response) {
    if (principal != null) {
      authService.logout(principal.userId());
    }
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<AuthenticatedUserView> me(
      @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
    if (principal == null) {
      throw new AccessDeniedException("Not authenticated");
    }
    return ResponseEntity.ok(authService.me(principal.userId()));
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Cookie helpers
  // ──────────────────────────────────────────────────────────────────────────

  private void addRefreshCookie(HttpServletResponse response, String token) {
    int maxAge = (int) Duration.parse(refreshTtlIso).getSeconds();
    String secure = cookieSecure ? "Secure; " : "";
    String domain =
        (cookieDomain != null && !cookieDomain.isBlank()) ? "; Domain=" + cookieDomain : "";
    response.setHeader(
        "Set-Cookie",
        String.format(
            "refresh_token=%s; Path=/api/auth; Max-Age=%d; HttpOnly; %sSameSite=Strict%s",
            token, maxAge, secure, domain));
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    String secure = cookieSecure ? "; Secure" : "";
    response.setHeader(
        "Set-Cookie",
        "refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict" + secure);
  }
}
