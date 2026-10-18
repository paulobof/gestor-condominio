package br.com.condominio.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.parkingrental.ParkingRentalController;
import br.com.condominio.feature.parkingrental.ParkingRentalService;
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

/** Rota não mapeada responde 404 (e não 500 pelo catch-all do {@link GlobalExceptionHandler}). */
@WebMvcTest(controllers = ParkingRentalController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UnmappedRouteWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ParkingRentalService service;
  @MockBean private JwtService jwtService;

  @Test
  void unmappedRoute_returns404() throws Exception {
    mvc.perform(get("/api/does-not-exist").with(MockAuth.user(UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
