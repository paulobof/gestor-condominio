package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberDetail;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.time.LocalDate;
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
 * Contrato HTTP do {@link UnitMemberController}: toda a superfície exige a permission {@code
 * RESIDENT_MANAGE}; sem ela (morador comum) recebe 403; payload inválido → 400.
 */
@WebMvcTest(controllers = UnitMemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class UnitMemberControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final String MANAGE = "RESIDENT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private UnitMemberService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private static final String VALID_BODY =
      "{\"fullName\":\"Maria Silva\",\"greetingName\":\"Maria\",\"email\":\"maria@test.com\","
          + "\"phone\":\"11999998888\",\"whatsappOptIn\":true}";

  private static final String INVALID_PHONE_BODY =
      "{\"fullName\":\"Maria Silva\",\"greetingName\":\"Maria\",\"email\":\"maria@test.com\","
          + "\"phone\":\"abc\",\"whatsappOptIn\":true}";

  @Test
  void listMy_withPermission_returns200() throws Exception {
    when(service.listMyUnitMembers(UID)).thenReturn(List.of());
    mvc.perform(get("/api/units/me/members").with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isOk());
  }

  @Test
  void listMy_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/units/me/members").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listMyUnitMembers(any());
  }

  @Test
  void create_withPermission_returns201_andShowsProvisionalPassword() throws Exception {
    when(service.createMember(eq(UID), any()))
        .thenReturn(
            new CreatedUnitMemberResponse(UUID.randomUUID(), "Maria Silva", "Prov!1234abcd"));

    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").value("Prov!1234abcd"));
  }

  @Test
  void create_withoutPermission_returns403() throws Exception {
    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden());
    verify(service, never()).createMember(any(), any());
  }

  @Test
  void create_invalidPhone_returns400() throws Exception {
    mvc.perform(
            post("/api/units/me/members")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVALID_PHONE_BODY))
        .andExpect(status().isBadRequest());
    verify(service, never()).createMember(any(), any());
  }

  @Test
  void update_withPermission_returns204() throws Exception {
    UUID memberId = UUID.randomUUID();
    mvc.perform(
            put("/api/units/me/members/{id}", memberId)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isNoContent());
    verify(service).updateMember(eq(UID), eq(memberId), any());
  }

  @Test
  void update_withoutPermission_returns403() throws Exception {
    mvc.perform(
            put("/api/units/me/members/{id}", UUID.randomUUID())
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden());
    verify(service, never()).updateMember(any(), any(), any());
  }

  @Test
  void delete_withPermission_returns204() throws Exception {
    UUID memberId = UUID.randomUUID();
    mvc.perform(delete("/api/units/me/members/{id}", memberId).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).deleteMember(UID, memberId);
  }

  @Test
  void delete_withoutPermission_returns403() throws Exception {
    mvc.perform(delete("/api/units/me/members/{id}", UUID.randomUUID()).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).deleteMember(any(), any());
  }

  @Test
  void getDetail_withPermission_returns200WithGenderAndBirthDate() throws Exception {
    UUID memberId = UUID.randomUUID();
    when(service.getMemberDetail(UID, memberId))
        .thenReturn(
            new UnitMemberDetail(
                memberId,
                "Bia Souza",
                "Bia",
                "+5511988887777",
                "bia@test.com",
                "FEMALE",
                LocalDate.of(1990, 1, 2)));

    mvc.perform(get("/api/units/me/members/{id}", memberId).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gender").value("FEMALE"))
        .andExpect(jsonPath("$.birthDate").value("1990-01-02"));
  }

  @Test
  void getDetail_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/units/me/members/{id}", UUID.randomUUID()).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).getMemberDetail(any(), any());
  }
}
