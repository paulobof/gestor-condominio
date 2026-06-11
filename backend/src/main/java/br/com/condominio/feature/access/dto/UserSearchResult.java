package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Projeção de usuário para a lista de acessos. {@code unitLabel}/{@code phone} podem ser nulos. */
public record UserSearchResult(UUID id, String displayName, String unitLabel, String phone) {}
