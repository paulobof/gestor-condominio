package br.com.condominio.shared.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {
  private int loginPerMinPerIp = 5;
  private int refreshPerMinPerIp = 10;
  private int registerGuestPerMinPerIp = 5;
  private int loginLockoutAttempts = 10;
  private Duration loginLockoutWindow = Duration.ofMinutes(30);
}
