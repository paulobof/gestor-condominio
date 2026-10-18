package br.com.condominio.feature.complaint.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BatchRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> ids) {}
