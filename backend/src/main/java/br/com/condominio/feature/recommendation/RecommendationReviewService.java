package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.ReviewRequest;
import br.com.condominio.feature.recommendation.dto.ReviewView;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Avaliação de indicações: uma por (indicação, apartamento). Cria, edita e apaga a avaliação do
 * próprio apartamento, lista com anonimato entre vizinhos e modera (oculta), mantendo os contadores
 * desnormalizados de {@link Recommendation} sincronizados.
 */
@Service
@RequiredArgsConstructor
public class RecommendationReviewService {

  private final RecommendationRepository repo;
  private final RecommendationReviewRepository reviewRepo;
  private final UserRepository userRepo;
  private final UnitRepository unitRepo;

  @Transactional
  public ReviewView create(
      UUID recommendationId, UUID actorId, UUID actorUnitId, ReviewRequest req) {
    if (actorUnitId == null) {
      throw new RecommendationException(
          "REVIEW_REQUIRES_UNIT", "Só moradores com unidade podem avaliar indicações.");
    }
    reviewRepo
        .findByRecommendationIdAndUnitId(recommendationId, actorUnitId)
        .ifPresent(
            existing -> {
              throw new RecommendationException(
                  "REVIEW_UNIT_ALREADY_REVIEWED", "Este apartamento já avaliou esta indicação.");
            });
    RecommendationReview review =
        RecommendationReview.create(
            recommendationId,
            actorUnitId,
            actorId,
            req.sentiment(),
            req.rating().shortValue(),
            req.text());
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
    return toViews(List.of(review), actorId, actorUnitId, false).get(0);
  }

  @Transactional
  public ReviewView edit(UUID recommendationId, UUID reviewId, UUID actorId, ReviewRequest req) {
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    review.edit(req.sentiment(), req.rating().shortValue(), req.text());
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
    return toViews(List.of(review), actorId, review.getUnitId(), false).get(0);
  }

  @Transactional
  public void delete(UUID recommendationId, UUID reviewId, UUID actorId) {
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    reviewRepo.delete(review);
    recountAndSave(load(recommendationId));
  }

  /**
   * Lista as avaliações da indicação. Sem moderação, avaliações ocultas somem da lista. O nome do
   * autor e o código da unidade só aparecem para quem modera ou mora na mesma unidade da avaliação
   * — para todo o resto, {@code authorName}/{@code unitCode} vêm nulos.
   */
  @Transactional
  public List<ReviewView> list(
      UUID recommendationId, UUID actorId, UUID actorUnitId, boolean canModerate) {
    List<RecommendationReview> reviews =
        reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(recommendationId);
    List<RecommendationReview> visible =
        canModerate
            ? reviews
            : reviews.stream().filter(r -> r.getStatus() == ReviewStatus.ACTIVE).toList();
    return toViews(visible, actorId, actorUnitId, canModerate);
  }

  /** Oculta a avaliação (moderação). Nunca apaga — recontabiliza para tirá-la das médias. */
  @Transactional
  public void hide(UUID recommendationId, UUID reviewId) {
    RecommendationReview review =
        reviewRepo
            .findByIdAndRecommendationId(reviewId, recommendationId)
            .orElseThrow(
                () -> new RecommendationException("NOT_FOUND", "Avaliação não encontrada."));
    review.hide();
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
  }

  private List<ReviewView> toViews(
      List<RecommendationReview> reviews, UUID actorId, UUID actorUnitId, boolean canModerate) {
    // PII (nome, unidade) só é buscada para quem tem direito de vê-la: escopa o lote às
    // avaliações reveladas, nunca a todas — senão carrega nome/unidade de todo mundo à toa.
    List<RecommendationReview> revealable =
        reviews.stream().filter(r -> reveals(r, actorUnitId, canModerate)).toList();
    Set<UUID> authorIds =
        revealable.stream().map(RecommendationReview::getAuthorUserId).collect(Collectors.toSet());
    Map<UUID, String> names =
        authorIds.isEmpty()
            ? Map.of()
            : userRepo.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    Set<UUID> unitIds =
        revealable.stream().map(RecommendationReview::getUnitId).collect(Collectors.toSet());
    Map<UUID, String> codes =
        unitIds.isEmpty()
            ? Map.of()
            : unitRepo.findAllById(unitIds).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getCode));
    return reviews.stream()
        .map(r -> toView(r, actorId, actorUnitId, canModerate, names, codes))
        .toList();
  }

  private boolean reveals(RecommendationReview r, UUID actorUnitId, boolean canModerate) {
    return canModerate || (actorUnitId != null && actorUnitId.equals(r.getUnitId()));
  }

  private ReviewView toView(
      RecommendationReview r,
      UUID actorId,
      UUID actorUnitId,
      boolean canModerate,
      Map<UUID, String> names,
      Map<UUID, String> codes) {
    boolean mine = r.getAuthorUserId().equals(actorId);
    boolean reveal = reveals(r, actorUnitId, canModerate);
    return new ReviewView(
        r.getId(),
        r.getSentiment().name(),
        r.getRating(),
        r.getText(),
        r.getCreatedAt(),
        mine,
        reveal ? names.get(r.getAuthorUserId()) : null,
        reveal ? codes.get(r.getUnitId()) : null);
  }

  /** Recalcula os quatro contadores a partir das avaliações ATIVAS. Oculta não conta. */
  void recountAndSave(Recommendation r) {
    List<RecommendationReview> active =
        reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(r.getId()).stream()
            .filter(rev -> rev.getStatus() == ReviewStatus.ACTIVE)
            .toList();
    int likes =
        (int) active.stream().filter(rev -> rev.getSentiment() == ReviewSentiment.LIKE).count();
    int ratingSum = active.stream().mapToInt(RecommendationReview::getRating).sum();
    r.updateReviewCounts(likes, active.size() - likes, active.size(), ratingSum);
    repo.save(r);
  }

  private Recommendation load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Indicação não encontrada."));
  }

  private RecommendationReview loadOwned(UUID recommendationId, UUID reviewId, UUID actorId) {
    RecommendationReview review =
        reviewRepo
            .findByIdAndRecommendationId(reviewId, recommendationId)
            .orElseThrow(
                () -> new RecommendationException("NOT_FOUND", "Avaliação não encontrada."));
    if (!review.getAuthorUserId().equals(actorId)) {
      throw new RecommendationException("FORBIDDEN", "Sem permissão sobre esta avaliação.");
    }
    return review;
  }
}
