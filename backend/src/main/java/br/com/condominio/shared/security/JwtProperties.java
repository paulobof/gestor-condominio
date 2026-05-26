package br.com.condominio.shared.security;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
  private String issuer;
  private String audience;
  private Duration accessTtl = Duration.ofMinutes(15);
  private Duration refreshTtl = Duration.ofDays(7);
  private String activeKid;
  private List<String> keys = List.of();
}
