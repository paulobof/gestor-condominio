package br.com.condominio.feature.whatsapp;

/**
 * Falha enviar mensagem via bot WhatsApp. Lançada pelo {@link WhatsAppNotificationClient} para que
 * {@code @Retry} / {@code @CircuitBreaker} do Resilience4j reaja. Nunca propaga até o caller HTTP —
 * o listener async absorve e marca a outbox como FAILED.
 */
public class WhatsAppSendException extends RuntimeException {
  public WhatsAppSendException(String message) {
    super(message);
  }

  public WhatsAppSendException(String message, Throwable cause) {
    super(message, cause);
  }
}
