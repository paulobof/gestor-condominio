package br.com.condominio.feature.featureflag;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato do {@link FeatureController}: público (a tela de login precisa dele antes de existir
 * sessão) e reflete exatamente as propriedades do ambiente.
 */
@WebMvcTest(controllers = FeatureController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class, FeatureFlags.class})
@TestPropertySource(
    properties = {
      "app.feature.announcements.enabled=true",
      "app.feature.classifieds.enabled=true",
      "app.feature.parkingrental.enabled=false"
    })
class FeatureControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void list_isPublicAndReflectsEnvironment() throws Exception {
    mvc.perform(get("/api/features"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.announcements").value(true))
        .andExpect(jsonPath("$.classifieds").value(true))
        .andExpect(jsonPath("$.parkingrental").value(false));
  }

  @Test
  void list_defaultsToEnabledWhenPropertyAbsent() throws Exception {
    // Ausencia de variavel significa RODANDO (2026-08-26): desligar e ato explicito no ambiente.
    // Antes era o contrario, e flag esquecida no Dokploy virava "menu aparece, API 404".
    mvc.perform(get("/api/features"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessmanagement").value(true));
  }
}
