package br.com.condominio.feature.tag.dto;

import br.com.condominio.feature.tag.Tag;
import java.util.UUID;

public record TagView(UUID id, String slug, String label, String color) {
  public static TagView of(Tag t) {
    return new TagView(t.getId(), t.getSlug(), t.getLabel(), t.getColor());
  }
}
