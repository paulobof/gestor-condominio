package br.com.condominio.feature.classified;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "classified")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE classified SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Classified {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClassifiedStatus status;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

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

  public static Classified create(
      UUID authorUserId, String title, String description, BigDecimal price) {
    Classified c = new Classified();
    c.authorUserId = authorUserId;
    c.title = title;
    c.description = description;
    c.price = price;
    c.status = ClassifiedStatus.ACTIVE;
    return c;
  }

  public void edit(String title, String description, BigDecimal price) {
    this.title = title;
    this.description = description;
    this.price = price;
  }

  public void markSold() {
    if (status != ClassifiedStatus.ACTIVE) {
      throw new IllegalStateException("Só anúncios ativos podem ser marcados como vendidos.");
    }
    status = ClassifiedStatus.SOLD;
  }

  public void archive() {
    if (status == ClassifiedStatus.ARCHIVED) {
      throw new IllegalStateException("Anúncio já está arquivado.");
    }
    status = ClassifiedStatus.ARCHIVED;
  }

  public void reactivate() {
    if (status == ClassifiedStatus.ACTIVE) {
      throw new IllegalStateException("Anúncio já está ativo.");
    }
    status = ClassifiedStatus.ACTIVE;
  }
}
