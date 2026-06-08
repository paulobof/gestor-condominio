package br.com.condominio.feature.faq.dto;

import br.com.condominio.feature.faq.Faq;
import java.time.Instant;
import java.util.UUID;

public record FaqView(
    UUID id,
    String question,
    String answer,
    String category,
    boolean published,
    int ordering,
    Instant updatedAt) {

  public static FaqView of(Faq f) {
    return new FaqView(
        f.getId(),
        f.getQuestion(),
        f.getAnswer(),
        f.getCategory(),
        f.isPublished(),
        f.getOrdering(),
        f.getUpdatedAt());
  }
}
