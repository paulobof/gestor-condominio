package br.com.condominio.feature.registration.event;

import java.util.UUID;

/**
 * Alguém se cadastrou numa unidade que já tem master. O master decide se aprova — este evento
 * dispara o aviso por WhatsApp para ele (AFTER_COMMIT, ver {@code UnitJoinRequestedEventListener}).
 *
 * @param requestUserId usuário criado em PENDING_APPROVAL
 * @param masterPhone telefone do master (destinatário)
 * @param masterGreetingName como chamar o master na mensagem
 * @param requesterName nome de quem pediu acesso
 * @param unitCode unidade pedida (ex.: 702C)
 */
public record UnitJoinRequestedEvent(
    UUID requestUserId,
    String masterPhone,
    String masterGreetingName,
    String requesterName,
    String unitCode) {}
