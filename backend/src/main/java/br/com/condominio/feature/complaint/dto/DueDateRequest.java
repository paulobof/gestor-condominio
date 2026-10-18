package br.com.condominio.feature.complaint.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DueDateRequest(@NotNull LocalDate dueDate) {}
