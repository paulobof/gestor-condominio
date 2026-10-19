package br.com.condominio.feature.password.event;

import java.util.UUID;

/**
 * Evento disparado por {@code PasswordResetService.issueInvite} quando um usuário é cadastrado por
 * terceiros (master da unidade ou admin). O listener async (AFTER_COMMIT) envia pelo WhatsApp o
 * link para a pessoa criar a própria senha. O {@code rawToken} só existe em memória; no banco fica
 * apenas o hash.
 */
public record UserInvitedEvent(
    UUID userId, UUID tokenId, String rawToken, String phone, String greetingName, long ttlHours) {

  /** Não vaza token nem telefone em log/exceção. */
  @Override
  public String toString() {
    return "UserInvitedEvent[userId=" + userId + ", tokenId=" + tokenId + "]";
  }
}
