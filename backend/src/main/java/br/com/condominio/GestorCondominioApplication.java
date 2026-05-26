package br.com.condominio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class GestorCondominioApplication {

  public static void main(String[] args) {
    SpringApplication.run(GestorCondominioApplication.class, args);
  }
}
// rebuild trigger 2026-05-26-1640
