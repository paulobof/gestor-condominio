package br.com.condominio.feature.complaint;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint_photo")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "ordering"})
@SQLDelete(sql = "UPDATE complaint_photo SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ComplaintPhoto {

  @Id @GeneratedValue private UUID id;

  @Column(name = "complaint_id", nullable = false, updatable = false)
  private UUID complaintId;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(nullable = false)
  private int ordering;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static ComplaintPhoto create(
      UUID complaintId, String objectKey, String contentType, long sizeBytes, int ordering) {
    ComplaintPhoto p = new ComplaintPhoto();
    p.complaintId = complaintId;
    p.objectKey = objectKey;
    p.contentType = contentType;
    p.sizeBytes = sizeBytes;
    p.ordering = ordering;
    return p;
  }
}
