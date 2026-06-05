package br.com.condominio.feature.recommendation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationProperties {
  private String consentBaseUrl = "http://localhost:5173/indicacoes/pendentes";

  public String buildConsentLink() {
    return consentBaseUrl;
  }
}
