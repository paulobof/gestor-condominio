package br.com.condominio.feature.unit;

import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Auto-registro de posse de unidade extra pelo usuário logado. Gated pela flag {@code
 * app.feature.unitownership.enabled} (off → 404). Abre um claim PENDING; não altera {@code
 * User.unitId}.
 */
@RestController
@RequestMapping("/api/auth/me/unit-claims")
@ConditionalOnProperty(name = "app.feature.unitownership.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MyUnitClaimController {

  private final UnitOwnershipService service;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> claim(
      @RequestParam("unitCode") String unitCode,
      @RequestPart("proof") MultipartFile proof,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.claimExtraUnit(me.userId(), unitCode, proof);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
