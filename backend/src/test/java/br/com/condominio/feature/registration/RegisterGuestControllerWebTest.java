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
 * Contrato HTTP do {@link RegisterGuestController}: cadastro de convidado é público, JSON, retorna
 * 202 ACTIVE e valida campos obrigatórios.
 */
@WebMvcTest(controllers = RegisterGuestController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterGuestControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService;

  private static final String VALID_JSON =
      """
      {"fullName":"Convidado Teste","greetingName":"Convidado","email":"%s",
       "phone":"11999998888","gender":"NOT_INFORMED","password":"Senha@1234",
       "consentVersion":"v3","whatsappOptIn":true,"captchaToken":"tok"}
      """;

  @Test
  void registerGuest_returns202_active() throws Exception {
    when(service.registerGuest(any(), any()))
        .thenReturn(new RegistrationStatusResponse(UUID.randomUUID(), "ACTIVE"));

    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_JSON.formatted("guest@test.com")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(service).registerGuest(any(), any());
  }

  @Test
  void registerGuest_invalidEmail_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_JSON.formatted("naoEhEmail")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerGuest(any(), any());
  }

  @Test
  void registerGuest_weakPassword_returns400() throws Exception {
    String weak =
        """
        {"fullName":"Convidado","greetingName":"Convidado","email":"guest@test.com",
         "phone":"11999998888","password":"senha12345","consentVersion":"v3",
         "whatsappOptIn":true}
        """;
    mvc.perform(
            post("/api/auth/register-guest").contentType(MediaType.APPLICATION_JSON).content(weak))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerGuest(any(), any());
  }
}
