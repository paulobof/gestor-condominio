package br.com.condominio.feature.whatsapp;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do WhatsApp outbound. {@code hmacKeys} no formato {@code v1:base64,v2:base64} permite
 * rotação sem downtime — o bot do Paulo sabe qual chave usar pelo header {@code X-Hmac-Kid}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
  private String webhookUrl;
  private String hmacKeys;
  private String hmacActiveKid = "v1";
  private int timeoutMs = 5000;
  private int maxRetries = 5;
  private int retryIntervalMs = 60_000;
  private int antiReplayWindowSeconds = 5;

  /** Mapa kid → bytes da chave (decode base64). Construído eagerly no startup. */
  public Map<String, byte[]> parsedHmacKeys() {
    if (hmacKeys == null || hmacKeys.isBlank()) {
      throw new IllegalStateException("app.whatsapp.hmac-keys nao configurado");
    }
    Map<String, byte[]> map = new HashMap<>();
    for (String entry : hmacKeys.split(",")) {
      String trimmed = entry.trim();
      int sep = trimmed.indexOf(':');
      if (sep <= 0 || sep >= trimmed.length() - 1) {
        throw new IllegalStateException(
            "hmac-keys entry invalido: '" + trimmed + "' (formato esperado: kid:base64)");
      }
      String kid = trimmed.substring(0, sep);
      byte[] key = Base64.getDecoder().decode(trimmed.substring(sep + 1));
      map.put(kid, key);
    }
    if (!map.containsKey(hmacActiveKid)) {
      throw new IllegalStateException(
          "hmac-active-kid='" + hmacActiveKid + "' nao encontrado em hmac-keys: " + map.keySet());
    }
    return Map.copyOf(map);
  }

  public Duration timeout() {
    return Duration.ofMillis(timeoutMs);
  }
}
