package br.com.condominio.feature.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;

/**
 * Voto (like/dislike) de um usuário numa indicação — uma linha por (indicação, usuário). É uma
 * reação: alternar/remover é hard delete da linha (não é histórico de negócio), por isso sem soft
 * delete. Os contadores agregados ficam denormalizados em {@code recommendation.like_count}.
 */
@Entity
@Table(name = "recommendation_vote")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class RecommendationVote {

  @Id @GeneratedValue private UUID id;

  @Column(name = "recommendation_id", nullable = false, updatable = false)
  private UUID recommendationId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private VoteValue value;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public static RecommendationVote create(UUID recommendationId, UUID userId, VoteValue value) {
    RecommendationVote v = new RecommendationVote();
    v.recommendationId = recommendationId;
    v.userId = userId;
    v.value = value;
    return v;
  }

  public void changeValue(VoteValue value) {
    this.value = value;
  }
}
