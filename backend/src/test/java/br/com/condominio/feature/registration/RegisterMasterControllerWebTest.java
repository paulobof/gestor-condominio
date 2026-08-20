package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link RegisterMasterController}: cadastro é público, JSON (sem comprovante),
 * retorna 202 com o status resolvido pelo service (ACTIVE se a unidade não tinha master,
 * PENDING_APPROVAL se tinha) e valida os campos obrigatórios.
 */
@WebMvcTest(controllers = RegisterMasterController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterMasterControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private String body(String email, String password) {
    return """
        {"fullName":"Paulo Teste","greetingName":"Paulo","email":"%s","phone":"11999998888",
         "unitCode":"A-101","password":"%s","consentVersion":"v3","whatsappOptIn":true}
        """
        .formatted(email, password);
  }

  @Test
  void registerMaster_returns202_active() throws Exception {
    when(service.registerMaster(any(), any()))
        .thenReturn(new RegistrationStatusResponse(UUID.randomUUID(), "ACTIVE"));

    mvc.perform(
            post("/api/auth/register-master")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("paulo@test.com", "Senha@1234")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(service).registerMaster(any(), any());
  }

  @Test
  void registerMaster_whenUnitHasMaster_returns202_pending() throws Exception {
    when(service.registerMaster(any(), any()))
        .thenReturn(new RegistrationStatusResponse(UUID.randomUUID(), "PENDING_APPROVAL"));

    mvc.perform(
            post("/api/auth/register-master")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("paulo@test.com", "Senha@1234")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
  }

  @Test
  void registerMaster_invalidEmail_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/register-master")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("naoEhEmail", "Senha@1234")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerMaster(any(), any());
  }

  @Test
  void registerMaster_weakPassword_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/register-master")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("paulo@test.com", "senha12345")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerMaster(any(), any());
  }
}
