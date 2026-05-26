package br.com.condominio.feature.auth.dto;

public record LoginResponse(String accessToken, AuthenticatedUserView user) {}
