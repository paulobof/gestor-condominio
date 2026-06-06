package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link UnitMemberController}: gestão de membros é restrita ao master da unidade
 * ({@code isUnitMaster}); não-master autenticado recebe 403 em todas as operações.
 */
@WebMvcTest(controllers = UnitMemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UnitMemberControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private UnitMemberService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private static final String VALID_BODY =
      "{\"fullName\":\"Maria Silva\",\"greetingName\":\"Maria\",\"email\":\"maria@test.com\","
          + "\"phone\":\"11999998888\",\"password\":\"senha12345\",\"whatsappOptIn\":true}";

  @Test
  void listMy_asMaster_returns200() throws Exception {
    when(service.listMyUnitMembers(UID)).thenReturn(List.of());
    mvc.perform(get("/api/units/me/members").with(MockAuth.master(UID))).andExpect(status().isOk());
  }

  @Test
  void listMy_asNonMaster_returns403() throws Exception {
    mvc.perform(get("/api/units/me/members").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listMyUnitMembers(any());
  }

  @Test
  void create_asMaster_returns201() throws Exception {
    when(service.createMember(eq(UID), any()))
        .thenReturn(
            new UnitMemberResponse(
                UUID.randomUUID(),
                "Maria Silva",
                "Maria",
                "maria@test.com",
                "11999998888",
                "ACTIVE"));

    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.master(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("maria@test.com"));
  }

  @Test
  void create_asNonMaster_returns403() throws Exception {
    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden());
    verify(service, never()).createMember(any(), any());
  }

  @Test
  void disable_asMaster_returns204() throws Exception {
    UUID memberId = UUID.randomUUID();
    mvc.perform(put("/api/units/me/members/{id}/disable", memberId).with(MockAuth.master(UID)))
        .andExpect(status().isNoContent());
    verify(service).disableMember(UID, memberId);
  }

  @Test
  void disable_asNonMaster_returns403() throws Exception {
    mvc.perform(
            put("/api/units/me/members/{id}/disable", UUID.randomUUID()).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).disableMember(any(), any());
  }
}
