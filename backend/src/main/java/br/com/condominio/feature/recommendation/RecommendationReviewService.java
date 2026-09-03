package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.ReviewRequest;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Avaliação de indicações: uma por (indicação, apartamento). Cria, edita e apaga a avaliação do
 * próprio apartamento e mantém os contadores desnormalizados de {@link Recommendation}
 * sincronizados. Listagem e moderação (anonimato, ocultar) são responsabilidade de outro serviço.
 */
@Service
@RequiredArgsConstructor
public class RecommendationReviewService {

  private final RecommendationRepository repo;
  private final RecommendationReviewRepository reviewRepo;

  @Transactional
  public void create(UUID recommendationId, UUID actorId, UUID actorUnitId, ReviewRequest req) {
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
  }

  @Transactional
  public void edit(UUID recommendationId, UUID reviewId, UUID actorId, ReviewRequest req) {
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    review.edit(req.sentiment(), req.rating().shortValue(), req.text());
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
  }

  @Transactional
  public void delete(UUID recommendationId, UUID reviewId, UUID actorId) {
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    reviewRepo.delete(review);
    recountAndSave(load(recommendationId));
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
