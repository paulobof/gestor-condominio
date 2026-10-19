package br.com.condominio.feature.user.dto;

/**
 * Resposta do reenvio do convite de primeiro acesso: para onde o link foi enviado, com o telefone
 * mascarado (só os 4 últimos dígitos, ex. "•••• 7777").
 */
public record InviteSentResponse(String inviteSentTo) {}
