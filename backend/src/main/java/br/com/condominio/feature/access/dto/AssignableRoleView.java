package br.com.condominio.feature.access.dto;

/** Role exibível na tela de acessos (apenas as {@code assignable}). */
public record AssignableRoleView(short id, String name, String label) {}
