package br.com.condominio.feature.announcement;

import br.com.condominio.feature.announcement.dto.AnnouncementView;
import br.com.condominio.feature.announcement.dto.CreateAnnouncementRequest;
import br.com.condominio.feature.announcement.dto.UpdateAnnouncementRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Mural de avisos. Leitura para qualquer autenticado; escrita só com {@code ANNOUNCEMENT_MANAGE}.
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.announcements.enabled", havingValue = "true")
public class AnnouncementController {

  private final AnnouncementService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<AnnouncementView> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return service.list(PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public AnnouncementView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
  public ResponseEntity<AnnouncementView> create(
      @Valid @RequestBody CreateAnnouncementRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
  public AnnouncementView update(
      @PathVariable UUID id, @Valid @RequestBody UpdateAnnouncementRequest body) {
    return service.update(id, body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
