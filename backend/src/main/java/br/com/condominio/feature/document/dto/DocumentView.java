package br.com.condominio.feature.document.dto;

import br.com.condominio.feature.document.Document;
import br.com.condominio.feature.document.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record DocumentView(
    UUID id,
    String title,
    DocumentType type,
    String filename,
    String contentType,
    long sizeBytes,
    UUID uploadedByUserId,
    Instant createdAt) {

  public static DocumentView of(Document d) {
    return new DocumentView(
        d.getId(),
        d.getTitle(),
        d.getType(),
        d.getFilename(),
        d.getContentType(),
        d.getSizeBytes(),
        d.getUploadedByUserId(),
        d.getCreatedAt());
  }
}
