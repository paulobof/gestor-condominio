package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.dto.ComplaintView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.complaints.enabled", havingValue = "true")
public class ComplaintController {

  static final String MANAGE = "COMPLAINT_MANAGE";

  private final ComplaintService service;

  static boolean canManage(AuthenticatedUserPrincipal me) {
    return me.authorities().contains(MANAGE);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ComplaintView> create(
      @Valid @RequestBody ComplaintFields body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(me.userId(), me.unitId(), body));
  }

  @GetMapping("/mine")
  @PreAuthorize("isAuthenticated()")
  public List<ComplaintView> mine(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.mine(me.userId());
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ComplaintView get(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.get(id, me.userId(), canManage(me));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ComplaintView update(
      @PathVariable UUID id,
      @Valid @RequestBody ComplaintFields body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.update(id, me.userId(), body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.cancel(id, me.userId());
    return ResponseEntity.noContent().build();
  }
}
