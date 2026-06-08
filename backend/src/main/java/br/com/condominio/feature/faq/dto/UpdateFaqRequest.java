package br.com.condominio.feature.faq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFaqRequest(
    @NotBlank @Size(max = 200) String question,
    @NotBlank String answer,
    @NotBlank @Size(max = 80) String category,
    boolean published) {}
