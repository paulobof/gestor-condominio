package br.com.condominio.feature.classified;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Com a feature flag de classificados desligada, o {@code @ConditionalOnProperty} não registra o
 * controller — endpoint ausente (404) mesmo autenticado.
 */
@WebMvcTest(
    controllers = ClassifiedController.class,
    properties = "app.feature.classifieds.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ClassifiedFeatureFlagOffWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ClassifiedService service;
  @MockBean private JwtService jwtService;

  @Test
  void endpoint_isAbsent_whenFlagOff() throws Exception {
    mvc.perform(get("/api/classifieds").with(MockAuth.user(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }
}
