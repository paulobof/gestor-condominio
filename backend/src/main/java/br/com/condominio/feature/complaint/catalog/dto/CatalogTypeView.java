package br.com.condominio.feature.complaint.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CatalogTypeView(
    UUID id, String name, boolean active, int sortOrder, List<CatalogItemView> subtypes) {}
