package br.com.condominio.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  @Bean
  public ApplicationRunner replacePendingAdminHash(
      @Value("${app.admin.initial-password}") String initialPassword,
      @Value("${app.security.password.pepper-version:1}") int pepperVersion) {
    return args -> {
      String newHash = encoder.encode(initialPassword);
      int rows =
          jdbc.update(
              """
              UPDATE "user"
                 SET password_hash = ?, password_pepper_version = ?
               WHERE password_hash = '__PENDING__'
              """,
              newHash,
              pepperVersion);
      if (rows > 0) {
        log.info("AdminBootstrap: substituido password_hash de {} usuario(s) __PENDING__", rows);
      } else {
        log.debug(
            "AdminBootstrap: nenhum usuario com password_hash=__PENDING__ encontrado (idempotente)");
      }
    };
  }
}
