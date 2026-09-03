package br.com.condominio.feature.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Avaliação de um apartamento sobre uma indicação: uma por (indicação, unidade), com sentimento,
 * nota e comentário obrigatório. Conteúdo → soft delete (padrão do projeto).
 */
@Entity
@Table(name = "recommendation_review")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE recommendation_review SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class RecommendationReview {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @Column(name = "recommendation_id", nullable = false, updatable = false)
  private UUID recommendationId;

  @Column(name = "unit_id", nullable = false, updatable = false)
  private UUID unitId;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ReviewSentiment sentiment;

  @Column(nullable = false)
  private short rating;

  @Column(columnDefinition = "text", nullable = false)
  private String text;

  @Enumerated(EnumType.STRING)
  @ToString.Include
  @Column(nullable = false, length = 10)
  private ReviewStatus status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static RecommendationReview create(
      UUID recommendationId,
      UUID unitId,
      UUID authorUserId,
      ReviewSentiment sentiment,
      short rating,
      String text) {
    RecommendationReview r = new RecommendationReview();
    r.recommendationId = recommendationId;
    r.unitId = unitId;
    r.authorUserId = authorUserId;
    r.sentiment = sentiment;
    r.rating = rating;
    r.text = text.strip();
    r.status = ReviewStatus.ACTIVE;
    return r;
  }

  public void edit(ReviewSentiment sentiment, short rating, String text) {
    this.sentiment = sentiment;
    this.rating = rating;
    this.text = text.strip();
  }

  public void hide() {
    this.status = ReviewStatus.HIDDEN;
  }
}
