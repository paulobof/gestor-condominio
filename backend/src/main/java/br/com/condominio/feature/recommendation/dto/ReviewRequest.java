package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.ReviewSentiment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Avaliação de uma indicação: sentimento, nota e comentário — os três obrigatórios. */
public record ReviewRequest(
    @NotNull ReviewSentiment sentiment,
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotBlank @Size(min = 10, max = 2000) String text) {}
