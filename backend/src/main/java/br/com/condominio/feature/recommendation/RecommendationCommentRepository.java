package br.com.condominio.feature.recommendation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationCommentRepository
    extends JpaRepository<RecommendationComment, UUID> {

  List<RecommendationComment> findByRecommendationIdOrderByCreatedAtAsc(UUID recommendationId);

  long countByRecommendationId(UUID recommendationId);

  Optional<RecommendationComment> findByIdAndRecommendationId(UUID id, UUID recommendationId);

  /** Contagem de comentários por indicação, em lote (para a listagem). */
  @Query(
      "select c.recommendationId as rid, count(c) as cnt from RecommendationComment c "
          + "where c.recommendationId in :ids group by c.recommendationId")
  List<CommentCount> countByRecommendationIdIn(Collection<UUID> ids);

  interface CommentCount {
    UUID getRid();

    long getCnt();
  }
}
