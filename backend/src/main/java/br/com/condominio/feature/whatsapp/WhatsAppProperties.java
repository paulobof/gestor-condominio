package br.com.condominio.feature.whatsapp;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do WhatsApp outbound via Evolution GO ({@code evo.paulobof.com.br}). O envio é {@code POST
 * {baseUrl}/send/text} autenticado pelo header {@code apikey} (o token da instância já seleciona o
 * número que envia). O texto é renderizado no backend ({@link WhatsAppMessageRenderer}), não no
 * gateway.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
  /** Base URL do Evolution (sem barra final), ex.: {@code https://evo.paulobof.com.br}. */
  private String baseUrl;

  /** Token da instância (header {@code apikey}). Segredo — fora de logs/toString. */
  private String apiKey;

  /**
   * Nome da instância (ex.: {@code Bot-Robo}). Informativo/log — no Evolution GO a instância é
   * selecionada pelo {@code apiKey} (token da instância), não pela URL.
   */
  private String instance;

  private int timeoutMs = 5000;
  private int maxRetries = 5;
  private int retryIntervalMs = 60_000;

  public Duration timeout() {
    return Duration.ofMillis(timeoutMs);
  }
}
