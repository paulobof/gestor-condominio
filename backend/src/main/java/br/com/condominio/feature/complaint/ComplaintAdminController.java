package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/complaints")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.complaints.enabled", havingValue = "true")
public class ComplaintAdminController {

  private static final String CAN = "hasAuthority('COMPLAINT_MANAGE')";

  private final ComplaintAdminService service;

  @GetMapping
  @PreAuthorize(CAN)
  public Page<ComplaintAdminView> queue(
      @RequestParam(name = "status", required = false) Set<ComplaintStatus> status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.queue(status == null ? Set.of() : status, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/{id}/start-review")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView startReview(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.startReview(id, me.userId());
  }

  @PostMapping("/{id}/ready")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView ready(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.markReady(id, me.userId());
  }

  @PostMapping("/{id}/reopen-review")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView reopenReview(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.reopenReview(id, me.userId());
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView reject(
      @PathVariable UUID id,
      @Valid @RequestBody RejectRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.reject(id, me.userId(), body.reason());
  }

  @PutMapping("/{id}")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView edit(
      @PathVariable UUID id,
      @Valid @RequestBody ComplaintFields body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.edit(id, me.userId(), body);
  }

  @PutMapping("/{id}/due-date")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView dueDate(
      @PathVariable UUID id,
      @Valid @RequestBody DueDateRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.setDueDate(id, me.userId(), body.dueDate());
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize(CAN)
  public ComplaintAdminDetailView resolve(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) ResolveRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.resolve(id, me.userId(), body == null ? null : body.note());
  }
}
