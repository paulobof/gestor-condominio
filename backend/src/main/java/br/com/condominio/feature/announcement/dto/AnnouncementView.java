package br.com.condominio.feature.announcement.dto;

import br.com.condominio.feature.announcement.Announcement;
import br.com.condominio.feature.announcement.AnnouncementImportance;
import java.time.Instant;
import java.util.UUID;

public record AnnouncementView(
    UUID id,
    String title,
    String body,
    int position,
    Instant publishedAt,
    UUID authorUserId,
    Instant updatedAt,
    AnnouncementImportance importance) {

  public static AnnouncementView of(Announcement a) {
    return new AnnouncementView(
        a.getId(),
        a.getTitle(),
        a.getBody(),
        a.getPosition(),
        a.getPublishedAt(),
        a.getAuthorUserId(),
        a.getUpdatedAt(),
        a.getImportance());
  }
}
