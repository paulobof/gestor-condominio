package br.com.condominio.feature.classified.dto;

import br.com.condominio.feature.classified.Classified;
import br.com.condominio.feature.classified.ClassifiedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClassifiedView(
    UUID id,
    String title,
    String description,
    BigDecimal price,
    ClassifiedStatus status,
    UUID authorUserId,
    Instant createdAt,
    List<ClassifiedPhotoView> photos) {

  public static ClassifiedView of(Classified c, List<ClassifiedPhotoView> photos) {
    return new ClassifiedView(
        c.getId(),
        c.getTitle(),
        c.getDescription(),
        c.getPrice(),
        c.getStatus(),
        c.getAuthorUserId(),
        c.getCreatedAt(),
        photos);
  }
}
