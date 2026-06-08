package br.com.condominio.feature.faq;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Pergunta frequente publicada pelo síndico/conselho. Soft delete; agrupada por categoria. */
@Entity
@Table(name = "faq")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE faq SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Faq {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 200)
  private String question;

  @Column(columnDefinition = "text", nullable = false)
  private String answer;

  @Column(nullable = false, length = 80)
  private String category;

  @Column(nullable = false)
  private boolean published;

  @Column(nullable = false)
  private int ordering;

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

  public static Faq create(
      String question, String answer, String category, boolean published, int ordering) {
    Faq f = new Faq();
    f.question = question;
    f.answer = answer;
    f.category = category;
    f.published = published;
    f.ordering = ordering;
    return f;
  }

  public void edit(String question, String answer, String category) {
    this.question = question;
    this.answer = answer;
    this.category = category;
  }

  public void setPublishedFlag(boolean published) {
    this.published = published;
  }

  public void setOrderingValue(int ordering) {
    this.ordering = ordering;
  }
}
