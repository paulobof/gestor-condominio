package br.com.condominio.feature.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Documento do condomínio (RI, AGE, atas, ...). Arquivo (PDF) fica no MinIO; aqui só os metadados.
 * Soft delete via {@code @SQLDelete}/{@code @SQLRestriction} (entidade {@code @Version}).
 */
@Entity
@Table(name = "document")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "type", "title"})
@SQLDelete(sql = "UPDATE document SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Document {

  @Id @GeneratedValue private UUID id;

  @Version private Long version;

  @Column(nullable = false, length = 180)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentType type;

  @Column(name = "object_key", nullable = false, columnDefinition = "text")
  private String objectKey;

  @Column(nullable = false, length = 255)
  private String filename;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
  private UUID uploadedByUserId;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static Document create(
      String title,
      DocumentType type,
      String objectKey,
      String filename,
      String contentType,
      long sizeBytes,
      UUID uploadedByUserId) {
    Document d = new Document();
    d.title = title;
    d.type = type;
    d.objectKey = objectKey;
    d.filename = filename;
    d.contentType = contentType;
    d.sizeBytes = sizeBytes;
    d.uploadedByUserId = uploadedByUserId;
    Instant now = Instant.now();
    d.createdAt = now;
    d.updatedAt = now;
    return d;
  }
}
