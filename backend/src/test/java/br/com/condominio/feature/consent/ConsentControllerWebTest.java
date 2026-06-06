package br.com.condominio.feature.consent;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.consent.dto.ConsentDocumentView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link ConsentController}: documento de privacidade vigente é público (sem
 * autenticação); 200 quando existe, 404 quando não há documento publicado.
 */
@WebMvcTest(controllers = ConsentController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ConsentControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ConsentService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void current_whenPublished_returns200_public() throws Exception {
    when(service.current())
        .thenReturn(Optional.of(new ConsentDocumentView("v3", "corpo", Instant.now())));

    mvc.perform(get("/api/privacy/document/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("v3"));
  }

  @Test
  void current_whenNone_returns404() throws Exception {
    when(service.current()).thenReturn(Optional.empty());
    mvc.perform(get("/api/privacy/document/current")).andExpect(status().isNotFound());
  }
}
