package br.com.condominio.feature.complaint.catalog.dto;

import br.com.condominio.feature.complaint.catalog.MoveDirection;
import jakarta.validation.constraints.NotNull;

public record MoveRequest(@NotNull MoveDirection direction) {}
