package br.com.condominio.feature.complaint.batch;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint_batch")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "complaintCount"})
@SQLDelete(sql = "UPDATE complaint_batch SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ComplaintBatch {

  @Id @GeneratedValue private UUID id;

  @Column(name = "sent_by_user_id", nullable = false, updatable = false)
  private UUID sentByUserId;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  @Column(name = "rendered_text", nullable = false, updatable = false, columnDefinition = "text")
  private String renderedText;

  @Column(name = "complaint_count", nullable = false, updatable = false)
  private int complaintCount;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static ComplaintBatch create(UUID sentBy, Instant sentAt, String text, int count) {
    ComplaintBatch b = new ComplaintBatch();
    b.sentByUserId = sentBy;
    b.sentAt = sentAt;
    b.renderedText = text;
    b.complaintCount = count;
    return b;
  }
}
