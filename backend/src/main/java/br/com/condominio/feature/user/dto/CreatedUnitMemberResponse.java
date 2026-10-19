package br.com.condominio.feature.user.dto;

import java.util.UUID;

/**
 * Resposta do cadastro de morador. Não há senha: a pessoa recebe pelo WhatsApp o link para criar a
 * sua. {@code inviteSentTo} é o telefone mascarado (só os 4 últimos dígitos, ex. "•••• 7777").
 */
public record CreatedUnitMemberResponse(UUID id, String fullName, String inviteSentTo) {}
