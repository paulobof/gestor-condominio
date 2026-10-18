package br.com.condominio.feature.complaint.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogItemRequest(
    @NotBlank @Size(max = 80) String name,
    @NotNull @Min(0) Integer sortOrder,
    @NotNull Boolean active) {}
