package br.com.condominio.feature.password;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {
  private Duration ttl = Duration.ofMinutes(30);

  /** Validade do link de convite (criar a primeira senha) enviado no cadastro por terceiros. */
  private Duration inviteTtl = Duration.ofHours(72);

  private String baseUrl = "http://localhost:5173/reset";

  public String buildResetLink(String rawToken) {
    String sep = baseUrl.contains("?") ? "&" : "?";
    return baseUrl + sep + "token=" + rawToken;
  }

  /**
   * Link de convite: o mesmo do reset, marcado como primeiro acesso para a tela adaptar o texto.
   */
  public String buildInviteLink(String rawToken) {
    return buildResetLink(rawToken) + "&primeiro=1";
  }
}
