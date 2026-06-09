package br.com.condominio.feature.info.dto;

import br.com.condominio.feature.info.InfoSection;
import java.time.Instant;
import java.util.UUID;

public record InfoSectionView(UUID id, String title, String body, int position, Instant updatedAt) {

  public static InfoSectionView of(InfoSection s) {
    return new InfoSectionView(
        s.getId(), s.getTitle(), s.getBody(), s.getPosition(), s.getUpdatedAt());
  }
}
