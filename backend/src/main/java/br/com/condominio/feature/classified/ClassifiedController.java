package br.com.condominio.feature.classified;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/classifieds")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.classifieds.enabled", havingValue = "true")
public class ClassifiedController {

  private final ClassifiedService service;

  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    return me.authorities().contains("CLASSIFIED_MODERATE");
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ClassifiedView> list(
      @RequestParam(required = false) ClassifiedStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return service.list(status, PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ClassifiedView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ClassifiedView> create(
      @Valid @RequestBody CreateClassifiedRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ClassifiedView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateClassifiedRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.update(id, me.userId(), canModerate(me), body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.delete(id, me.userId(), canModerate(me));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/photos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ClassifiedPhotoView> addPhoto(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.addPhoto(id, me.userId(), canModerate(me), file));
  }

  @DeleteMapping("/{id}/photos/{photoId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> removePhoto(
      @PathVariable UUID id,
      @PathVariable UUID photoId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.removePhoto(id, photoId, me.userId(), canModerate(me));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/photos/{photoId}/url")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> photoUrl(
      @PathVariable UUID id, @PathVariable UUID photoId) {
    String url = service.photoUrl(id, photoId);
    return ResponseEntity.ok().header("Referrer-Policy", "no-referrer").body(Map.of("url", url));
  }
}
