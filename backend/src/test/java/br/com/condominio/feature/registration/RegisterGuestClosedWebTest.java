package br.com.condominio.feature.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O cadastro de Convidado saiu do {@code permitAll}: o papel GUEST existe no banco (V29) mas a
 * feature não tem tela, então o endpoint não pode ficar como superfície pública de criação de
 * conta. Volta a ser público quando a feature entrar.
 */
@WebMvcTest(controllers = RegisterGuestController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegisterGuestClosedWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void registerGuest_anonymous_isRejected() throws Exception {
    mvc.perform(
            post("/api/auth/register-guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"Convidado\",\"greetingName\":\"Convidado\","
                        + "\"email\":\"g@x.com\",\"phone\":\"11999998888\","
                        + "\"password\":\"Senha@1234\",\"consentVersion\":\"v3\","
                        + "\"whatsappOptIn\":false}"))
        .andExpect(status().isUnauthorized());

    verify(service, never()).registerGuest(any(), any());
  }
}
