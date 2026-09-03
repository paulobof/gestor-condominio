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
    @NotBlank @Size(min = 10, max = 2000) String text) {

  // Apara antes da validação: sem isso, um texto com padding de espaços (ex.: 9 espaços + 1
  // caractere) passa em @NotBlank/@Size(min=10) sobre a string crua e vira comentário de 1
  // caractere após o strip() da entidade — violando "mínimo 10 caracteres após trim" e batendo
  // na CHECK chk_review_text do banco (500 em vez de 400).
  public ReviewRequest {
    text = text == null ? null : text.strip();
  }
}
