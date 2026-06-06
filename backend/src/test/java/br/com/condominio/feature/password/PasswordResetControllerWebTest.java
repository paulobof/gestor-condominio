package br.com.condominio.feature.password;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Contrato HTTP do {@link PasswordResetController} (endpoints públicos): request-reset sempre 202
 * (não vaza existência do usuário), consume-reset 204 / 400 em token inválido, e validação de body.
 */
@WebMvcTest(controllers = PasswordResetController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class PasswordResetControllerWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private PasswordResetService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void requestReset_returns202_andDelegates() throws Exception {
    mvc.perform(
            post("/api/auth/password/request-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"paulo@test.com\"}"))
        .andExpect(status().isAccepted());
    verify(service).requestReset(eq("paulo@test.com"), any());
  }

  @Test
  void requestReset_invalidEmail_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/password/request-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"naoEhEmail\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).requestReset(any(), any());
  }

  @Test
  void consumeReset_returns204_andDelegates() throws Exception {
    mvc.perform(
            post("/api/auth/password/consume-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-123\",\"newPassword\":\"novaSenha123\"}"))
        .andExpect(status().isNoContent());
    verify(service).consumeReset(eq("tok-123"), eq("novaSenha123"), any());
  }

  @Test
  void consumeReset_invalidToken_returns400() throws Exception {
    doThrow(new PasswordResetException("INVALID_TOKEN", "token inválido ou já usado"))
        .when(service)
        .consumeReset(any(), any(), any());

    mvc.perform(
            post("/api/auth/password/consume-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-ruim\",\"newPassword\":\"novaSenha123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void consumeReset_shortPassword_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/password/consume-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-123\",\"newPassword\":\"123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).consumeReset(any(), any(), any());
  }
}
