package br.com.condominio.feature.user.dto;

/**
 * Dados da própria conta exibidos em "Meu cadastro". O {@code phone} é o telefone privado da conta
 * (WhatsApp/recuperação de senha); {@code email} é o e-mail primário de login (somente leitura).
 */
public record OwnProfileView(String fullName, String greetingName, String phone, String email) {}
