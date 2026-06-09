package br.com.condominio.feature.info.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderInfoRequest(@NotEmpty List<Item> items) {
  public record Item(@NotNull UUID id, int position) {}
}
