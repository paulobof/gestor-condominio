package br.com.condominio.feature.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.auth.dto.AuthenticatedUserView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link AuthController}: login/refresh (públicos) com cookie HttpOnly de refresh,
 * 401 em credencial inválida, 403 em refresh sem cookie, e /me /logout exigindo autenticação.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class AuthControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private AuthService authService;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private AuthenticatedUserView userView() {
    return new AuthenticatedUserView(
        UID,
        "Paulo Teste",
        "Paulo",
        "paulo@test.com",
        null,
        false,
        List.of("MANAGER"),
        List.of("USER_VIEW"),
        false);
  }

  @Test
  void login_returns200_withAccessToken_andSetsRefreshCookie() throws Exception {
    when(authService.login("paulo@test.com", "senha123"))
        .thenReturn(new AuthService.LoginResult("acc-123", "ref-456", userView()));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"paulo@test.com\",\"password\":\"senha123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("acc-123"))
        .andExpect(jsonPath("$.user.email").value("paulo@test.com"))
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=ref-456")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
  }

  @Test
  void login_blankCredentials_returns400() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(authService, never()).login(any(), any());
  }

  @Test
  void login_badCredentials_returns401() throws Exception {
    when(authService.login(any(), any())).thenThrow(new BadCredentialsException("nope"));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"paulo@test.com\",\"password\":\"errada\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void refresh_missingCookie_returns403() throws Exception {
    mvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden());
    verify(authService, never()).refresh(any());
  }

  @Test
  void refresh_withCookie_returns200_andRotatesCookie() throws Exception {
    when(authService.refresh("ref-old"))
        .thenReturn(new AuthService.LoginResult("acc-new", "ref-new", userView()));

    mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "ref-old")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("acc-new"))
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=ref-new")));
  }

  @Test
  void refresh_replayOrInvalid_returns401() throws Exception {
    // RefreshTokenService lança SecurityException em token desconhecido/expirado/replay.
    when(authService.refresh("ref-bad"))
        .thenThrow(new SecurityException("Refresh token replay detected"));

    mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "ref-bad")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
  }

  @Test
  void me_authenticated_returns200() throws Exception {
    when(authService.me(UID)).thenReturn(userView());

    mvc.perform(get("/api/auth/me").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("paulo@test.com"));
  }

  @Test
  void me_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().is4xxClientError());
    verify(authService, never()).me(any());
  }

  @Test
  void logout_authenticated_returns204_andClearsCookie() throws Exception {
    mvc.perform(post("/api/auth/logout").with(MockAuth.user(UID)))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    verify(authService).logout(UID);
  }
}
