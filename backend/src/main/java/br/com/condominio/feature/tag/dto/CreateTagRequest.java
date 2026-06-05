package br.com.condominio.feature.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
    @NotBlank @Size(max = 80) String slug,
    @Size(max = 80) String label,
    @Size(max = 20) String color) {}
