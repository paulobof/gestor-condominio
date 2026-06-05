package br.com.condominio.feature.classified.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateClassifiedRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 5000) String description,
    @PositiveOrZero BigDecimal price) {}
