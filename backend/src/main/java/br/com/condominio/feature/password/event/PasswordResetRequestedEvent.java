package br.com.condominio.feature.password.event;

import java.util.UUID;

/**
 * Evento disparado após {@code PasswordResetService.requestReset} persistir o token. O {@code
 * rawToken} só existe em memória durante o request — nunca é persistido em texto, apenas o hash vai
 * pro DB. O listener async constrói o link e enfileira na outbox WhatsApp.
 */
public record PasswordResetRequestedEvent(
    UUID userId,
    UUID tokenId,
    String rawToken,
    String phone,
    String greetingName,
    long ttlMinutes) {}
