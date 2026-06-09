package br.com.condominio.feature.info;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Seção de informações gerais (título + corpo rich text sanitizado). Soft delete; ordem manual. */
@Entity
@Table(name = "info_section")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE info_section SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class InfoSection {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 120)
  private String title;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  @Column(nullable = false)
  private int position;

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

  public static InfoSection create(String title, String body, int position) {
    InfoSection s = new InfoSection();
    s.title = title;
    s.body = body;
    s.position = position;
    return s;
  }

  public void edit(String title, String body) {
    this.title = title;
    this.body = body;
  }

  public void moveTo(int position) {
    this.position = position;
  }
}
