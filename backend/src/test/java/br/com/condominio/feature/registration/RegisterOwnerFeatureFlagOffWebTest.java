package br.com.condominio.feature.registration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Com a feature flag desligada, o controller não é registrado: rota → 404. */
@WebMvcTest(
    controllers = RegisterOwnerController.class,
    properties = "app.feature.unitownership.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterOwnerFeatureFlagOffWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService;

  @Test
  void registerOwner_withFlagOff_returns404() throws Exception {
    mvc.perform(
            multipart("/api/auth/register-owner")
                .file(
                    new MockMultipartFile(
                        "proof", "matricula.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .param("fullName", "Dona Ana")
                .param("greetingName", "Ana")
                .param("email", "ana@example.com")
                .param("phone", "11999990000")
                .param("unitCode", "A101")
                .param("password", "Str0ng!Pass1")
                .param("consentVersion", "v1")
                .param("whatsappOptIn", "true"))
        .andExpect(status().isNotFound());
  }
}
