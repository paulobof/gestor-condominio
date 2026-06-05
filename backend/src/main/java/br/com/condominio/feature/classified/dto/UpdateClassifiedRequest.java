package br.com.condominio.feature.classified.dto;

import br.com.condominio.feature.classified.ClassifiedStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code status} nulo = mantém o estado atual; preenchido = aplica a transição de domínio. */
public record UpdateClassifiedRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 5000) String description,
    @PositiveOrZero BigDecimal price,
    ClassifiedStatus status) {}
