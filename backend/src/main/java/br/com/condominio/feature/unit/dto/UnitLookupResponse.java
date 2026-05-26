package br.com.condominio.feature.unit.dto;

import java.util.UUID;

public record UnitLookupResponse(UUID id, String code, boolean hasActiveMaster) {}
