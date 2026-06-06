package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Contrato HTTP do {@link RegisterMasterController}: cadastro do master é público, multipart
 * (campos + comprovante), retorna 202 PENDING e valida os campos obrigatórios.
 */
@WebMvcTest(controllers = RegisterMasterController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterMasterControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private MockMultipartHttpServletRequestBuilder withFields(
      MockMultipartHttpServletRequestBuilder req, String email) {
    return (MockMultipartHttpServletRequestBuilder)
        req.file(new MockMultipartFile("proof", "proof.pdf", "application/pdf", new byte[] {1, 2}))
            .param("fullName", "Paulo Teste")
            .param("greetingName", "Paulo")
            .param("email", email)
            .param("phone", "11999998888")
            .param("unitCode", "A-101")
            .param("password", "senha12345")
            .param("consentVersion", "v3")
            .param("whatsappOptIn", "true");
  }

  @Test
  void registerMaster_returns202_pending() throws Exception {
    when(service.registerMaster(any(), any(), any()))
        .thenReturn(new RegistrationStatusResponse(UUID.randomUUID(), "PENDING"));

    mvc.perform(withFields(multipart("/api/auth/register-master"), "paulo@test.com"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING"));

    verify(service).registerMaster(any(), any(), any());
  }

  @Test
  void registerMaster_invalidEmail_returns400() throws Exception {
    mvc.perform(withFields(multipart("/api/auth/register-master"), "naoEhEmail"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(service, never()).registerMaster(any(), any(), any());
  }
}
