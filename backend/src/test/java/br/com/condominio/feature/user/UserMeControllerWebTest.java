package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.privacy.PrivacyService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link UserMeController}: toggle do opt-in de WhatsApp (LGPD, consentimento
 * revogável) exige autenticação e delega o booleano ao service; ausente = false.
 */
@WebMvcTest(controllers = UserMeController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UserMeControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private PrivacyService privacyService;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void updateOptIn_true_returns204_andDelegates() throws Exception {
    mvc.perform(
            put("/api/users/me/whatsapp-opt-in")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optIn\":true}"))
        .andExpect(status().isNoContent());
    verify(privacyService).updateWhatsappOptIn(eq(UID), eq(true));
  }

  @Test
  void updateOptIn_missingField_defaultsToFalse() throws Exception {
    mvc.perform(
            put("/api/users/me/whatsapp-opt-in")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNoContent());
    verify(privacyService).updateWhatsappOptIn(eq(UID), eq(false));
  }

  @Test
  void updateOptIn_unauthenticated_isRejected() throws Exception {
    mvc.perform(
            put("/api/users/me/whatsapp-opt-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optIn\":true}"))
        .andExpect(status().is4xxClientError());
    verify(privacyService, never()).updateWhatsappOptIn(any(), anyBoolean());
  }
}
