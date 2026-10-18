package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.dto.ComplaintPhotoView;
import br.com.condominio.feature.complaint.dto.ComplaintView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.complaints.enabled", havingValue = "true")
public class ComplaintController {

  static final String MANAGE = "COMPLAINT_MANAGE";

  private final ComplaintService service;
  private final ComplaintPhotoService photos;

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

  @GetMapping("/board")
  @PreAuthorize("isAuthenticated()")
  public Page<ComplaintView> board(
      @RequestParam(required = false) UUID typeId,
      @RequestParam(required = false) UUID locationId,
      @RequestParam(required = false) ComplaintStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.board(
        me.userId(), me.unitId(), canManage(me), typeId, locationId, status, page, size);
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

  @PostMapping("/{id}/photos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ComplaintPhotoView> addPhoto(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(photos.addPhoto(id, me.userId(), file));
  }

  @DeleteMapping("/{id}/photos/{photoId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> removePhoto(
      @PathVariable UUID id,
      @PathVariable UUID photoId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    photos.removePhoto(id, photoId, me.userId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/photos/{photoId}/url")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> photoUrl(
      @PathVariable UUID id,
      @PathVariable UUID photoId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    String url = photos.photoUrl(id, photoId, me.userId(), canManage(me));
    return ResponseEntity.ok().header("Referrer-Policy", "no-referrer").body(Map.of("url", url));
  }
}
