package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationReviewRepository extends JpaRepository<RecommendationReview, UUID> {

  Optional<RecommendationReview> findByRecommendationIdAndUnitId(
      UUID recommendationId, UUID unitId);

  List<RecommendationReview> findByRecommendationIdOrderByCreatedAtDesc(UUID recommendationId);

  Optional<RecommendationReview> findByIdAndRecommendationId(UUID id, UUID recommendationId);
}
