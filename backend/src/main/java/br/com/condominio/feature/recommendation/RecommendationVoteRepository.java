package br.com.condominio.feature.recommendation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationVoteRepository extends JpaRepository<RecommendationVote, UUID> {

  Optional<RecommendationVote> findByRecommendationIdAndUserId(UUID recommendationId, UUID userId);

  long countByRecommendationIdAndValue(UUID recommendationId, VoteValue value);

  /** Votos do usuário num conjunto de indicações — resolve "meu voto" em lote na listagem. */
  List<RecommendationVote> findByUserIdAndRecommendationIdIn(
      UUID userId, Collection<UUID> recommendationIds);
}
