package br.com.condominio.feature.user;

import br.com.condominio.feature.privacy.PrivacyService;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
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
}
