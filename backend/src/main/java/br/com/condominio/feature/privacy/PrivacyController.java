package br.com.condominio.feature.privacy;

import br.com.condominio.feature.privacy.dto.AnonymizeRequest;
import br.com.condominio.feature.privacy.dto.PersonalDataExportResponse;
import br.com.condominio.feature.privacy.dto.ProcessingActivityView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

  private final PrivacyService service;

  /** Exporta todos os dados pessoais do titular logado (Art. 18, II LGPD). */
  @GetMapping("/me/export")
  @PreAuthorize("isAuthenticated()")
  public PersonalDataExportResponse exportSelf(
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.exportSelf(me.userId());
  }

  /** Lista as atividades de tratamento do controlador (Art. 9 LGPD). */
  @GetMapping("/me/processing-activities")
  @PreAuthorize("isAuthenticated()")
  public List<ProcessingActivityView> processingActivities() {
    return service.processingActivities();
  }

  /**
   * Anonimização irreversível (Art. 18, IV LGPD). Exige senha + texto literal "ANONIMIZAR". Retorna
   * 204; cliente perde a sessão (refresh tokens revogados).
   */
  @PostMapping("/me/anonymize")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> anonymize(
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      @Valid @RequestBody AnonymizeRequest req) {
    service.anonymizeSelf(me.userId(), req.currentPassword());
    return ResponseEntity.noContent().build();
  }
}
