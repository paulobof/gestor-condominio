package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.feature.recommendations.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecommendationController {

  private final RecommendationService service;
  private final RecommendationReviewService reviewService;

  /** Visitante (sem sessao) nunca modera. */
  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    if (me == null) return false;
    return me.authorities().contains("RECOMMENDATION_MODERATE");
  }

  @GetMapping
  public Page<RecommendationView> list(
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    // Visitante: sem userId, logo sem "meu voto" — a lista em si e publica.
    UUID actorId = me == null ? null : me.userId();
    return service.list(actorId, tag, search, PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/{id}")
  public RecommendationView get(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.getById(id, me == null ? null : me.userId(), canModerate(me));
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RecommendationView> create(
      @Valid @RequestBody CreateRecommendationRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(me.userId(), me.unitId(), me.unitId() != null, body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public RecommendationView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRecommendationRequest body,
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

  @PostMapping("/{id}/hide")
  @PreAuthorize("hasAuthority('RECOMMENDATION_MODERATE')")
  public ResponseEntity<Void> hide(@PathVariable UUID id) {
    service.hide(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/photos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RecommendationPhotoView> addPhoto(
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
  public ResponseEntity<Map<String, Object>> photoUrl(
      @PathVariable UUID id, @PathVariable UUID photoId) {
    return ResponseEntity.ok()
        .header("Referrer-Policy", "no-referrer")
        .body(Map.of("url", service.photoUrl(id, photoId)));
  }

  // ---- Avaliações (like/dislike + nota + comentário) -------------------------------

  @GetMapping("/{id}/reviews")
  public List<ReviewView> listReviews(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    boolean canModerate = me != null && me.authorities().contains("RECOMMENDATION_MODERATE");
    return reviewService.list(
        id, me == null ? null : me.userId(), me == null ? null : me.unitId(), canModerate);
  }

  @PostMapping("/{id}/reviews")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public ReviewView createReview(
      @PathVariable UUID id,
      @Valid @RequestBody ReviewRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return reviewService.create(id, me.userId(), me.unitId(), req);
  }

  @PutMapping("/{id}/reviews/{reviewId}")
  @PreAuthorize("isAuthenticated()")
  public ReviewView editReview(
      @PathVariable UUID id,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return reviewService.edit(id, reviewId, me.userId(), req);
  }

  @DeleteMapping("/{id}/reviews/{reviewId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> deleteReview(
      @PathVariable UUID id,
      @PathVariable UUID reviewId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    reviewService.delete(id, reviewId, me.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reviews/{reviewId}/hide")
  @PreAuthorize("hasAuthority('RECOMMENDATION_MODERATE')")
  public ResponseEntity<Void> hideReview(@PathVariable UUID id, @PathVariable UUID reviewId) {
    reviewService.hide(id, reviewId);
    return ResponseEntity.noContent().build();
  }
}
