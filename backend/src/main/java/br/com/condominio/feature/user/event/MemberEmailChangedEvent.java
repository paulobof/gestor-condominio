package br.com.condominio.feature.user.event;

import java.util.UUID;

/**
 * Evento disparado após {@code UnitMemberService.updateMember} detectar que o e-mail primário do
 * morador foi alterado. O listener async envia WhatsApp de notificação via outbox. O {@code
 * newEmail} é carregado apenas para eventual auditoria futura; o texto da mensagem não o expõe
 * (CLAUDE.md — nunca PII em mensagem de log/WhatsApp desnecessariamente).
 */
public record MemberEmailChangedEvent(UUID memberUserId, String phone, String greetingName) {}
