package br.com.condominio.feature.info.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInfoSectionRequest(
    @NotBlank @Size(max = 120) String title, @NotBlank String body) {}
