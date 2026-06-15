package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.VoteValue;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull VoteValue value) {}
