package br.com.condominio.feature.complaint.catalog.dto;

import br.com.condominio.feature.complaint.catalog.CatalogEntry;
import java.util.UUID;

public record CatalogItemView(UUID id, String name, boolean active, int sortOrder) {
  public static CatalogItemView of(CatalogEntry e) {
    return new CatalogItemView(e.getId(), e.getName(), e.isActive(), e.getSortOrder());
  }
}
