package br.com.condominio.feature.recommendation;

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
 * Com a feature flag desligada, o {@code @ConditionalOnProperty} não registra o controller, então
 * os endpoints não existem (404) mesmo para usuário autenticado — inverso do Plano 3B Task 12 Step
 * 2 ("confirmar que /api/recommendations responde, não 404" quando ligada).
 */
@WebMvcTest(
    controllers = RecommendationController.class,
    properties = "app.feature.recommendations.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RecommendationFeatureFlagOffWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RecommendationService service;
  @MockBean private JwtService jwtService;

  @Test
  void endpoint_isAbsent_whenFlagOff() throws Exception {
    mvc.perform(get("/api/recommendations").with(MockAuth.user(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }
}
