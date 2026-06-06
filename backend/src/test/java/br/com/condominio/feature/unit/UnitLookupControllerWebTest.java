package br.com.condominio.feature.unit;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.unit.dto.UnitLookupResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link UnitLookupController}: lookup de unidade por código é público (usado no
 * cadastro); 200 quando existe, 404 quando não.
 */
@WebMvcTest(controllers = UnitLookupController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UnitLookupControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private UnitService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void lookup_whenFound_returns200_public() throws Exception {
    when(service.lookupByCode("A-101"))
        .thenReturn(Optional.of(new UnitLookupResponse(UUID.randomUUID(), "A-101", true)));

    mvc.perform(get("/api/units/lookup").param("code", "A-101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("A-101"))
        .andExpect(jsonPath("$.hasActiveMaster").value(true));
  }

  @Test
  void lookup_whenNotFound_returns404() throws Exception {
    when(service.lookupByCode("Z-999")).thenReturn(Optional.empty());
    mvc.perform(get("/api/units/lookup").param("code", "Z-999")).andExpect(status().isNotFound());
  }
}
