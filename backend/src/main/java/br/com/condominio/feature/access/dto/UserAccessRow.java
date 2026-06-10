package br.com.condominio.feature.access.dto;

import java.util.List;
import java.util.UUID;

/** Linha da lista de acessos: usuário + perfis geríveis atuais. {@code unitLabel} pode ser nulo. */
public record UserAccessRow(UUID id, String displayName, String unitLabel, List<RoleBadge> roles) {}
