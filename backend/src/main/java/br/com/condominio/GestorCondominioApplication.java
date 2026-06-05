package br.com.condominio;

import br.com.condominio.feature.recommendation.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RecommendationProperties.class)
public class GestorCondominioApplication {

  public static void main(String[] args) {
    SpringApplication.run(GestorCondominioApplication.class, args);
  }
}
// rebuild trigger 2026-05-26-1640
