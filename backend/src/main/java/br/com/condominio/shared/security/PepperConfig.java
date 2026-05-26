package br.com.condominio.shared.security;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PepperConfig {

  @Bean
  public PasswordEncoder passwordEncoder(
      @Value("${app.security.password.pepper}") String pepperBase64,
      @Value("${app.security.password.pepper-version:1}") int pepperVersion,
      @Value("${app.security.password.bcrypt-strength:12}") int bcryptStrength) {
    byte[] pepper = Base64.getDecoder().decode(pepperBase64);
    return new PepperedBCryptPasswordEncoder(
        pepper, pepperVersion, new BCryptPasswordEncoder(bcryptStrength));
  }
}
