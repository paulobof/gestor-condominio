package br.com.condominio.feature.registration.dto;

import java.util.UUID;

public record RegistrationStatusResponse(UUID userId, String status) {}
