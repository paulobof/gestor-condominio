package br.com.condominio.feature.user;

import br.com.condominio.feature.privacy.PrivacyService;
import br.com.condominio.feature.user.dto.ChangeOwnPasswordRequest;
import br.com.condominio.feature.user.dto.OwnProfileView;
import br.com.condominio.feature.user.dto.UpdateOwnProfileRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Endpoints "self" sobre o usuário logado. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserMeController {

  private final PrivacyService privacyService;
  private final SelfProfileService selfProfileService;

  /**
   * Alterna o opt-in de comunicações WhatsApp (LGPD: consentimento revogável). Body: {@code
   * {"optIn": true|false}}.
   */
  @PutMapping("/whatsapp-opt-in")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> updateWhatsappOptIn(
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      @RequestBody Map<String, Boolean> body) {
    boolean optIn = body.getOrDefault("optIn", false);
    privacyService.updateWhatsappOptIn(me.userId(), optIn);
    return ResponseEntity.noContent().build();
  }

  /** "Meu cadastro": dados da própria conta (inclui o telefone, que o /auth/me não expõe). */
  @GetMapping("/profile")
  @PreAuthorize("isAuthenticated()")
  public OwnProfileView getProfile(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return selfProfileService.getProfile(me.userId());
  }

  /** "Meu cadastro": edita nome, saudação e telefone da própria conta. */
  @PutMapping("/profile")
  @PreAuthorize("isAuthenticated()")
  public OwnProfileView updateProfile(
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      @Valid @RequestBody UpdateOwnProfileRequest body) {
    return selfProfileService.updateProfile(
        me.userId(), body.fullName(), body.greetingName(), body.phone());
  }

  /** "Meu cadastro": troca a própria senha (exige a atual). A sessão corrente é mantida. */
  @PutMapping("/password")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      @Valid @RequestBody ChangeOwnPasswordRequest body) {
    selfProfileService.changePassword(me.userId(), body.currentPassword(), body.newPassword());
    return ResponseEntity.noContent().build();
  }
}
