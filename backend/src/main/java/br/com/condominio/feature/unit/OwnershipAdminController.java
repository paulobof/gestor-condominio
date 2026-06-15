package br.com.condominio.feature.unit;

import br.com.condominio.feature.audit.AuditWriter;
import br.com.condominio.feature.registration.dto.RejectRequest;
import br.com.condominio.feature.unit.dto.OwnershipClaimView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administração de pedidos de posse de unidade (proprietário). Gated pela flag {@code
 * app.feature.unitownership.enabled}: com a flag off o controller não é registrado (rotas → 404).
 * Reusa as permissions de cadastro (REGISTRATION_VIEW/APPROVE, RESIDENCE_PROOF_VIEW).
 */
@RestController
@RequestMapping("/api/ownership-claims")
@ConditionalOnProperty(name = "app.feature.unitownership.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OwnershipAdminController {

  private final UnitOwnershipService service;
  private final AuditWriter auditWriter;

  @GetMapping
  @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
  public Page<OwnershipClaimView> listPending(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      HttpServletRequest request) {
    auditWriter.logSensitiveAccess(me.userId(), null, "REGISTRATION_VIEW", request);
    return service.listPendingClaims(PageRequest.of(page, Math.min(size, 100)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('REGISTRATION_APPROVE')")
  public ResponseEntity<Void> approve(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.approve(id, me.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('REGISTRATION_APPROVE')")
  public ResponseEntity<Void> reject(
      @PathVariable UUID id,
      @Valid @RequestBody RejectRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.reject(id, me.userId(), body.reason());
    return ResponseEntity.noContent().build();
  }

  /** Stream do comprovante do claim pelo backend (MinIO privado), autenticado e auditado. */
  @GetMapping("/{id}/proof")
  @PreAuthorize("hasAuthority('RESIDENCE_PROOF_VIEW')")
  public ResponseEntity<byte[]> proof(
      @PathVariable UUID id,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      HttpServletRequest request) {
    UnitOwnershipService.ProofContent proof = service.getClaimProofContent(id);
    auditWriter.logProofAccess(me.userId(), id, request, 0);
    MediaType contentType =
        proof.contentType() != null
            ? MediaType.parseMediaType(proof.contentType())
            : MediaType.APPLICATION_OCTET_STREAM;
    String filename = proof.filename() != null ? proof.filename() : "comprovante";
    return ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .header("Referrer-Policy", "no-referrer")
        .body(proof.content());
  }
}
