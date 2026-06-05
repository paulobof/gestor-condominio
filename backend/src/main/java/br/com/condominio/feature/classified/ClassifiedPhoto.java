package br.com.condominio.feature.classified;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "classified_photo")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "ordering"})
@SQLDelete(sql = "UPDATE classified_photo SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClassifiedPhoto {

  @Id @GeneratedValue private UUID id;

  @Column(name = "classified_id", nullable = false, updatable = false)
  private UUID classifiedId;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Column(nullable = false)
  private int ordering;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static ClassifiedPhoto create(
      UUID classifiedId, String objectKey, String contentType, int ordering) {
    ClassifiedPhoto p = new ClassifiedPhoto();
    p.classifiedId = classifiedId;
    p.objectKey = objectKey;
    p.contentType = contentType;
    p.ordering = ordering;
    return p;
  }
}
