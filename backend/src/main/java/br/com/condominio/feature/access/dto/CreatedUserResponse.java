package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Resposta do cadastro: senha provisória mostrada uma única vez. */
public record CreatedUserResponse(UUID id, String fullName, String password) {}
