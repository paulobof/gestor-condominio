package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationPhotoRepository extends JpaRepository<RecommendationPhoto, UUID> {

  List<RecommendationPhoto> findByRecommendationIdOrderByOrdering(UUID recommendationId);

  long countByRecommendationId(UUID recommendationId);

  Optional<RecommendationPhoto> findByIdAndRecommendationId(UUID id, UUID recommendationId);

  @Query(
      "select coalesce(max(p.ordering), -1) from RecommendationPhoto p"
          + " where p.recommendationId = :id")
  int maxOrdering(UUID id);
}
