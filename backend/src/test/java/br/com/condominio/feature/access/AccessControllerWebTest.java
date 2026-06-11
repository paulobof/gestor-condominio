package br.com.condominio.feature.access;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UpdateUserRequest;
import br.com.condominio.feature.access.dto.UserAccessRow;
import br.com.condominio.feature.access.dto.UserDetail;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link AccessController}: feature flag ligada; todos os endpoints exigem {@code
 * ROLE_ASSIGN} (403 sem; 401 anônimo); limite atingido vira 409.
 */
@WebMvcTest(
    controllers = AccessController.class,
    properties = "app.feature.accessmanagement.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class AccessControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();
  private static final String ASSIGN = "ROLE_ASSIGN";
  private static final String MANAGE = "USER_MANAGE";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @MockBean private AccessService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void roles_withRoleAssign_returns200() throws Exception {
    when(service.assignableRoles())
        .thenReturn(List.of(new AssignableRoleView((short) 6, "MURAL_EDITOR", "Editor do Mural")));

    mvc.perform(get("/api/access/roles").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Editor do Mural"));
  }

  @Test
  void roles_withoutRoleAssign_returns403() throws Exception {
    mvc.perform(get("/api/access/roles").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).assignableRoles();
  }

  @Test
  void users_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/access/users").param("q", "ana")).andExpect(status().is4xxClientError());
    verify(service, never()).listUsers(any(), any());
  }

  @Test
  void users_withRoleAssign_returns200_pagedWithBadges() throws Exception {
    var row =
        new UserAccessRow(
            TARGET,
            "Ana Lima",
            "A-101",
            "+5511999999999",
            List.of(new RoleBadge((short) 6, "Editor do Mural")));
    when(service.listUsers("", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/access/users").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].displayName").value("Ana Lima"))
        .andExpect(jsonPath("$.content[0].phone").value("+5511999999999"))
        .andExpect(jsonPath("$.content[0].roles[0].label").value("Editor do Mural"))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void users_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/access/users").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listUsers(any(), any());
  }

  @Test
  void users_withoutToken_returns401() throws Exception {
    mvc.perform(get("/api/access/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void assign_withRoleAssign_returns204() throws Exception {
    mvc.perform(
            post("/api/access/users/{id}/roles/{roleId}", TARGET, 6)
                .with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isNoContent());
    verify(service).assign(eq(UID), eq(TARGET), eq((short) 6));
  }

  @Test
  void assign_withoutRoleAssign_returns403() throws Exception {
    mvc.perform(post("/api/access/users/{id}/roles/{roleId}", TARGET, 6).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).assign(any(), any(), eq((short) 6));
  }

  @Test
  void assign_atLimit_returns409() throws Exception {
    doThrow(new AccessException("ROLE_LIMIT_REACHED", "Limite de 3 atingido para Conselheiro."))
        .when(service)
        .assign(eq(UID), eq(TARGET), eq((short) 2));

    mvc.perform(
            post("/api/access/users/{id}/roles/{roleId}", TARGET, 2)
                .with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_LIMIT_REACHED"));
  }

  @Test
  void remove_withRoleAssign_returns204() throws Exception {
    mvc.perform(
            delete("/api/access/users/{id}/roles/{roleId}", TARGET, 6)
                .with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isNoContent());
    verify(service).remove(eq(UID), eq(TARGET), eq((short) 6));
  }

  @Test
  void remove_withoutRoleAssign_returns403() throws Exception {
    mvc.perform(delete("/api/access/users/{id}/roles/{roleId}", TARGET, 6).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).remove(any(), any(), eq((short) 6));
  }

  @Test
  void users_sizeAbove100_isCappedTo100() throws Exception {
    when(service.listUsers("", PageRequest.of(0, 100))).thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/api/access/users").param("size", "200").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk());

    verify(service).listUsers("", PageRequest.of(0, 100));
  }

  @Test
  void creatableRoles_withRoleAssign_returns200() throws Exception {
    when(service.creatableRoles())
        .thenReturn(List.of(new AssignableRoleView((short) 4, "RESIDENT", "Morador")));

    mvc.perform(get("/api/access/creatable-roles").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Morador"));
  }

  @Test
  void createUser_withUserManage_returns201_withPassword() throws Exception {
    when(service.createUser(eq(UID), any(CreateUserRequest.class)))
        .thenReturn(new CreatedUserResponse(TARGET, "Ana Lima", "Abc123!xYZ09__a"));
    var body =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));

    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").value("Abc123!xYZ09__a"));
  }

  @Test
  void createUser_withoutUserManage_returns403() throws Exception {
    var body =
        new CreateUserRequest("Ana Lima", "ana@x.com", "+5511999999999", null, List.of((short) 4));
    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, ASSIGN))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    verify(service, never()).createUser(any(), any());
  }

  @Test
  void createUser_emailTaken_returns409() throws Exception {
    doThrow(new AccessException("EMAIL_TAKEN", "E-mail já cadastrado."))
        .when(service)
        .createUser(eq(UID), any(CreateUserRequest.class));
    var body =
        new CreateUserRequest("Ana", "dup@x.com", "+5511999999999", null, List.of((short) 4));

    mvc.perform(
            post("/api/access/users")
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void deleteUser_withUserManage_returns204() throws Exception {
    mvc.perform(delete("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).deleteUser(UID, TARGET);
  }

  @Test
  void deleteUser_withoutUserManage_returns403() throws Exception {
    mvc.perform(delete("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isForbidden());
    verify(service, never()).deleteUser(any(), any());
  }

  @Test
  void deleteUser_self_returns409() throws Exception {
    doThrow(new AccessException("CANNOT_DELETE_SELF", "Você não pode excluir a si mesmo."))
        .when(service)
        .deleteUser(eq(UID), eq(UID));
    mvc.perform(delete("/api/access/users/{id}", UID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
  }

  @Test
  void userDetail_withUserManage_returns200() throws Exception {
    when(service.getUserDetail(TARGET))
        .thenReturn(
            new UserDetail(
                TARGET,
                "Ana Lima",
                "Ana",
                "+5511999999999",
                null,
                null,
                "ana@x.com",
                "FEMALE",
                null));

    mvc.perform(get("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@x.com"))
        .andExpect(jsonPath("$.fullName").value("Ana Lima"));
  }

  @Test
  void userDetail_withoutUserManage_returns403() throws Exception {
    mvc.perform(get("/api/access/users/{id}", TARGET).with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isForbidden());
    verify(service, never()).getUserDetail(any());
  }

  @Test
  void updateUser_withUserManage_returns204() throws Exception {
    var body =
        new UpdateUserRequest(
            "Ana Nova", "Ana", "+5511999999999", null, "new@x.com", "FEMALE", null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isNoContent());
    verify(service).updateUser(eq(UID), eq(TARGET), any(UpdateUserRequest.class));
  }

  @Test
  void updateUser_withoutUserManage_returns403() throws Exception {
    var body = new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "new@x.com", null, null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, ASSIGN))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    verify(service, never()).updateUser(any(), any(), any());
  }

  @Test
  void updateUser_emailTaken_returns409() throws Exception {
    doThrow(new AccessException("EMAIL_TAKEN", "E-mail já cadastrado."))
        .when(service)
        .updateUser(eq(UID), eq(TARGET), any(UpdateUserRequest.class));
    var body = new UpdateUserRequest("Ana", "Ana", "+5511999999999", null, "dup@x.com", null, null);
    mvc.perform(
            put("/api/access/users/{id}", TARGET)
                .with(MockAuth.user(UID, MANAGE))
                .contentType("application/json")
                .content(om.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }
}
