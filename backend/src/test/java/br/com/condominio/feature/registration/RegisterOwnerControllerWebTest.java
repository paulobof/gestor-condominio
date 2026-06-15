package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = RegisterOwnerController.class,
    properties = "app.feature.unitownership.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterOwnerControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService;

  private MockMultipartFile proof() {
    return new MockMultipartFile("proof", "matricula.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  @Test
  void registerOwner_returns202() throws Exception {
    when(service.registerOwner(any(), any(), any()))
        .thenReturn(
            new RegistrationStatusResponse(java.util.UUID.randomUUID(), "PENDING_APPROVAL"));
    mvc.perform(
            multipart("/api/auth/register-owner")
                .file(proof())
                .param("fullName", "Dona Ana")
                .param("greetingName", "Ana")
                .param("email", "ana@example.com")
                .param("phone", "11999990000")
                .param("unitCode", "A101")
                .param("password", "Str0ng!Pass1")
                .param("consentVersion", "v1")
                .param("whatsappOptIn", "true"))
        .andExpect(status().isAccepted());
  }

  @Test
  void registerOwner_invalidEmail_returns400() throws Exception {
    mvc.perform(
            multipart("/api/auth/register-owner")
                .file(proof())
                .param("fullName", "Dona Ana")
                .param("greetingName", "Ana")
                .param("email", "nao-email")
                .param("phone", "11999990000")
                .param("unitCode", "A101")
                .param("password", "Str0ng!Pass1")
                .param("consentVersion", "v1")
                .param("whatsappOptIn", "true"))
        .andExpect(status().isBadRequest());
  }
}
