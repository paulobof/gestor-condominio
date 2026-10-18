package br.com.condominio.feature.complaint.dto;

import jakarta.validation.constraints.Size;

/** {@code text} vazio/nulo limpa a resposta. */
public record SyndicResponseRequest(@Size(max = 2000) String text) {}
