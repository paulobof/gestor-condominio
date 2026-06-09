package br.com.condominio.feature.announcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.announcement.dto.AnnouncementView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link AnnouncementController}: feature flag; leitura para autenticados; escrita
 * exige {@code ANNOUNCEMENT_MANAGE} (403 sem); validação e exceção→status.
 */
@WebMvcTest(
    controllers = AnnouncementController.class,
    properties = "app.feature.announcements.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class AnnouncementControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID AID = UUID.randomUUID();
  private static final String MANAGE = "ANNOUNCEMENT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private AnnouncementService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private AnnouncementView view() {
    return new AnnouncementView(AID, "Manutenção", "corpo", 0, Instant.now(), UID, Instant.now());
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list(any())).thenReturn(new PageImpl<>(List.of(view()), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/announcements").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Manutenção"))
        .andExpect(jsonPath("$.content[0].position").value(0));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/announcements")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  @Test
  void create_withManage_returns201() throws Exception {
    when(service.create(eq(UID), any())).thenReturn(view());

    mvc.perform(
            post("/api/announcements")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Manutenção\",\"body\":\"corpo\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Manutenção"));
  }

  @Test
  void create_withoutManage_returns403() throws Exception {
    mvc.perform(
            post("/api/announcements")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Manutenção\",\"body\":\"corpo\"}"))
        .andExpect(status().isForbidden());
    verify(service, never()).create(any(), any());
  }

  @Test
  void create_blankTitle_returns400() throws Exception {
    mvc.perform(
            post("/api/announcements")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"body\":\"corpo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).create(any(), any());
  }

  @Test
  void delete_withoutManage_returns403() throws Exception {
    mvc.perform(delete("/api/announcements/{id}", AID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).delete(any());
  }

  @Test
  void delete_withManage_returns204() throws Exception {
    mvc.perform(delete("/api/announcements/{id}", AID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).delete(AID);
  }

  @Test
  void reorder_withManage_returns204() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void reorder_withoutManage_returns403() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void reorder_unauthenticated_isRejected() throws Exception {
    mvc.perform(
            put("/api/announcements/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + AID + "\",\"position\":0}]}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void get_notFound_mapsTo404() throws Exception {
    when(service.getById(AID))
        .thenThrow(new AnnouncementException("NOT_FOUND", "Aviso não encontrado."));

    mvc.perform(get("/api/announcements/{id}", AID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
