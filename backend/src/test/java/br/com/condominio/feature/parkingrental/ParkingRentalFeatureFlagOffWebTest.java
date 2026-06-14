package br.com.condominio.feature.parkingrental;

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

/** Com a feature flag desligada (default), o controller não é registrado: rotas → 404. */
@WebMvcTest(
    controllers = ParkingRentalController.class,
    properties = "app.feature.parkingrental.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ParkingRentalFeatureFlagOffWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private ParkingRentalService service;
  @MockBean private JwtService jwtService;

  @Test
  void list_withFlagOff_returns404() throws Exception {
    mvc.perform(get("/api/parking-rentals").with(MockAuth.user(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }
}
