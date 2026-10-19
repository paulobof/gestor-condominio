package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.privacy.PrivacyService;
import br.com.condominio.feature.user.dto.OwnProfileView;
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
 * revogável) exige autenticação e delega o booleano ao service; ausente = false. "Meu cadastro":
 * leitura/edição dos próprios dados e troca de senha (exige a senha atual).
 */
@WebMvcTest(controllers = UserMeController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UserMeControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private PrivacyService privacyService;
  @MockBean private SelfProfileService selfProfileService;
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

  // ===== Meu cadastro =====

  private static final OwnProfileView VIEW =
      new OwnProfileView("Ana Silva", "Ana", "+5511999999999", "ana@x.com");

  private static final String PROFILE_JSON =
      "{\"fullName\":\"Ana Silva\",\"greetingName\":\"Ana\",\"phone\":\"+5511999999999\"}";

  private static String pwdJson(String current, String next) {
    return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
  }

  @Test
  void getProfile_returnsOwnView() throws Exception {
    when(selfProfileService.getProfile(UID)).thenReturn(VIEW);

    mvc.perform(get("/api/users/me/profile").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ana Silva"))
        .andExpect(jsonPath("$.greetingName").value("Ana"))
        .andExpect(jsonPath("$.phone").value("+5511999999999"))
        .andExpect(jsonPath("$.email").value("ana@x.com"));
  }

  @Test
  void getProfile_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/users/me/profile")).andExpect(status().is4xxClientError());
    verify(selfProfileService, never()).getProfile(any());
  }

  @Test
  void updateProfile_valid_returns200_andDelegates() throws Exception {
    when(selfProfileService.updateProfile(UID, "Ana Silva", "Ana", "+5511999999999"))
        .thenReturn(VIEW);

    mvc.perform(
            put("/api/users/me/profile")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROFILE_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ana Silva"))
        .andExpect(jsonPath("$.email").value("ana@x.com"));
  }

  @Test
  void updateProfile_blankOptionalFields_areAccepted() throws Exception {
    when(selfProfileService.updateProfile(UID, "Ana Silva", "", "")).thenReturn(VIEW);

    mvc.perform(
            put("/api/users/me/profile")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ana Silva\",\"greetingName\":\"\",\"phone\":\"\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void updateProfile_blankName_returns400ValidationFailed() throws Exception {
    mvc.perform(
            put("/api/users/me/profile")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(selfProfileService, never()).updateProfile(any(), any(), any(), any());
  }

  @Test
  void updateProfile_tooLongGreeting_returns400ValidationFailed() throws Exception {
    mvc.perform(
            put("/api/users/me/profile")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ana\",\"greetingName\":\"" + "x".repeat(61) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void updateProfile_invalidPhone_returns400ValidationFailed() throws Exception {
    mvc.perform(
            put("/api/users/me/profile")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ana\",\"phone\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void updateProfile_unauthenticated_isRejected() throws Exception {
    mvc.perform(
            put("/api/users/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROFILE_JSON))
        .andExpect(status().is4xxClientError());
    verify(selfProfileService, never()).updateProfile(any(), any(), any(), any());
  }

  @Test
  void changePassword_valid_returns204_andDelegates() throws Exception {
    mvc.perform(
            put("/api/users/me/password")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pwdJson("Atual@123", "Nova@Senha123")))
        .andExpect(status().isNoContent());
    verify(selfProfileService).changePassword(UID, "Atual@123", "Nova@Senha123");
  }

  @Test
  void changePassword_weakNewPassword_returns400ValidationFailed() throws Exception {
    mvc.perform(
            put("/api/users/me/password")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pwdJson("Atual@123", "fraca")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(selfProfileService, never()).changePassword(any(), anyString(), anyString());
  }

  @Test
  void changePassword_missingCurrent_returns400ValidationFailed() throws Exception {
    mvc.perform(
            put("/api/users/me/password")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"Nova@Senha123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void changePassword_wrongCurrent_returns400InvalidPassword() throws Exception {
    doThrow(new SelfProfileException("INVALID_PASSWORD", "Senha atual incorreta."))
        .when(selfProfileService)
        .changePassword(UID, "Errada@123", "Nova@Senha123");

    mvc.perform(
            put("/api/users/me/password")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pwdJson("Errada@123", "Nova@Senha123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
  }

  @Test
  void changePassword_reused_returns400PasswordReused() throws Exception {
    doThrow(new SelfProfileException("PASSWORD_REUSED", "Você já usou essa senha recentemente."))
        .when(selfProfileService)
        .changePassword(UID, "Atual@123", "Antiga@123");

    mvc.perform(
            put("/api/users/me/password")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pwdJson("Atual@123", "Antiga@123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"))
        .andExpect(jsonPath("$.message").value("Você já usou essa senha recentemente."));
  }

  @Test
  void changePassword_unauthenticated_isRejected() throws Exception {
    mvc.perform(
            put("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pwdJson("Atual@123", "Nova@Senha123")))
        .andExpect(status().is4xxClientError());
    verify(selfProfileService, never()).changePassword(any(), anyString(), anyString());
  }
}
