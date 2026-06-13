package br.com.condominio.feature.whatsapp;

/**
 * Templates de mensagem WhatsApp suportados pelo bot do Paulo. O bot renderiza o texto final a
 * partir do template + data; a app só envia esse enum + os campos esperados.
 */
public enum WhatsAppTemplate {
  /** Reset de senha. Espera data: {@code {greetingName, link, ttlMinutes}}. */
  PASSWORD_RESET,
  /** Senha alterada com sucesso (informativo). Espera data: {@code {greetingName}}. */
  PASSWORD_CHANGED,
  /** Aviso de inatividade prestes a anonimizar conta. Espera: {@code {greetingName, daysLeft}}. */
  INACTIVITY_WARNING,
  /**
   * E-mail de acesso do morador alterado pelo master da unidade. Espera data: {@code
   * {greetingName}}.
   */
  MEMBER_EMAIL_CHANGED
}
