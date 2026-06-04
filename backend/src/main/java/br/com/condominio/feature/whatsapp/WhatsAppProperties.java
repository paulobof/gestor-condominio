package br.com.condominio.feature.whatsapp;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do WhatsApp outbound via Evolution API ({@code evo.paulobof.com.br}, contrato v2). O envio
 * é {@code POST {baseUrl}/message/sendText/{instance}} autenticado pelo header {@code apikey}. O
 * texto da mensagem é renderizado no backend ({@link WhatsAppMessageRenderer}), não no gateway.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
  /** Base URL do Evolution (sem barra final), ex.: {@code https://evo.paulobof.com.br}. */
  private String baseUrl;

  /** Token da instância (header {@code apikey}). Segredo — fora de logs/toString. */
  private String apiKey;

  /** Nome da instância no Evolution (path do sendText), não o UUID interno do manager. */
  private String instance;

  private int timeoutMs = 5000;
  private int maxRetries = 5;
  private int retryIntervalMs = 60_000;

  public Duration timeout() {
    return Duration.ofMillis(timeoutMs);
  }
}
