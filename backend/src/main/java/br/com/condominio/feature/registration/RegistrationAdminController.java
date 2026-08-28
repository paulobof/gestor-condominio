package br.com.condominio.feature.registration;

import br.com.condominio.feature.audit.AuditWriter;
import br.com.condominio.feature.registration.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationAdminController {

  private final RegistrationService service;
  private final AuditWriter auditWriter;

  @GetMapping
  @PreAuthorize("hasAuthority('REGISTRATION_VIEW')")
  public Page<PendingRegistrationView> listPending(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me,
      HttpServletRequest request) {
    auditWriter.logSensitiveAccess(me.userId(), null, "REGISTRATION_VIEW", request);
    return service.listPending(PageRequest.of(page, Math.min(size, 100)));
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
}
