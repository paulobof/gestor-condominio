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
  private String baseUrl = "http://localhost:5173/reset";

  public String buildResetLink(String rawToken) {
    String sep = baseUrl.contains("?") ? "&" : "?";
    return baseUrl + sep + "token=" + rawToken;
  }
}
