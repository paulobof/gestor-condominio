package br.com.condominio.feature.password.event;

import java.util.UUID;

/**
 * Evento informativo disparado após o consumo bem-sucedido do token. Listener envia WhatsApp "sua
 * senha foi alterada".
 */
public record PasswordResetCompletedEvent(UUID userId, String phone, String greetingName) {}
