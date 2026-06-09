package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Resultado de busca de usuário para a tela de acessos. {@code unitLabel} pode ser nulo. */
public record UserSearchResult(UUID id, String displayName, String unitLabel) {}
