package br.com.condominio.feature.complaint.catalog.dto;

import java.util.List;

public record CatalogView(List<CatalogTypeView> types, List<CatalogItemView> locations) {}
