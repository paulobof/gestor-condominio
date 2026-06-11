package br.com.condominio.feature.access.dto;

import java.util.List;
import java.util.UUID;

/**
 * Linha da lista de acessos: usuário + perfis geríveis. {@code unitLabel}/{@code phone} nulos ok.
 */
public record UserAccessRow(
    UUID id, String displayName, String unitLabel, String phone, List<RoleBadge> roles) {}
