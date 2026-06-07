package br.com.condominio.feature.announcement;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Aviso do mural publicado pelo síndico. Soft delete; fixados aparecem primeiro na listagem. */
@Entity
@Table(name = "announcement")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE announcement SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Announcement {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 140)
  private String title;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  @Column(nullable = false)
  private boolean pinned;

  @Column(name = "published_at", insertable = false, updatable = false)
  private Instant publishedAt;

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

  public static Announcement create(UUID authorUserId, String title, String body, boolean pinned) {
    Announcement a = new Announcement();
    a.authorUserId = authorUserId;
    a.title = title;
    a.body = body;
    a.pinned = pinned;
    return a;
  }

  public void edit(String title, String body, boolean pinned) {
    this.title = title;
    this.body = body;
    this.pinned = pinned;
  }
}
