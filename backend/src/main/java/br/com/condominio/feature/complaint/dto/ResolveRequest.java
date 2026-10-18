package br.com.condominio.feature.complaint.dto;

import jakarta.validation.constraints.Size;

public record ResolveRequest(@Size(max = 500) String note) {}
