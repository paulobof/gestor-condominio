package br.com.condominio.feature.announcement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderAnnouncementsRequest(@NotEmpty List<Item> items) {
  public record Item(@NotNull UUID id, int position) {}
}
