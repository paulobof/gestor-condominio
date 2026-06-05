package br.com.condominio.feature.tag;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "tag")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "slug"})
@SQLDelete(sql = "UPDATE tag SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Tag {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, columnDefinition = "citext")
  private String slug;

  @Column(nullable = false, length = 80)
  private String label;

  @Column(length = 20)
  private String color;

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

  public static Tag create(String slug, String label, String color) {
    Tag t = new Tag();
    t.slug = slug;
    t.label = label;
    t.color = color;
    return t;
  }
}
