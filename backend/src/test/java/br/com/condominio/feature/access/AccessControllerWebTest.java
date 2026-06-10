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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UserAccessRow;
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

  @Autowired private MockMvc mvc;
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
            TARGET, "Ana Lima", "A-101", List.of(new RoleBadge((short) 6, "Editor do Mural")));
    when(service.listUsers("", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/access/users").with(MockAuth.user(UID, ASSIGN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].displayName").value("Ana Lima"))
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
}
