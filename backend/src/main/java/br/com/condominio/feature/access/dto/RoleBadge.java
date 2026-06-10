package br.com.condominio.feature.access.dto;

/** Perfil gerível que um usuário possui, para exibição em badge na lista de acessos. */
public record RoleBadge(short id, String label) {}
