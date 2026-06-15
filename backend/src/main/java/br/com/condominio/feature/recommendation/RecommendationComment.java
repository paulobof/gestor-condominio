package br.com.condominio.feature.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Comentário de um usuário numa indicação. Conteúdo → soft delete (padrão do projeto). */
@Entity
@Table(name = "recommendation_comment")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@SQLDelete(
    sql = "UPDATE recommendation_comment SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class RecommendationComment {

  @Id @GeneratedValue private UUID id;

  @Version private Long version;

  @Column(name = "recommendation_id", nullable = false, updatable = false)
  private UUID recommendationId;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Column(columnDefinition = "text", nullable = false)
  private String text;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static RecommendationComment create(
      UUID recommendationId, UUID authorUserId, String text) {
    RecommendationComment c = new RecommendationComment();
    c.recommendationId = recommendationId;
    c.authorUserId = authorUserId;
    c.text = text;
    return c;
  }
}
