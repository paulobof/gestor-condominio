package br.com.condominio.feature.info;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.info.dto.InfoSectionView;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = InfoSectionController.class,
    properties = "app.feature.generalinfo.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class InfoSectionControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID SID = UUID.randomUUID();
  private static final String MANAGE = "INFO_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private InfoSectionService service;
  @MockBean private JwtService jwtService;

  private InfoSectionView view() {
    return new InfoSectionView(SID, "Portaria", "<p>24h</p>", 0, Instant.now());
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list()).thenReturn(List.of(view()));

    mvc.perform(get("/api/info-sections").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Portaria"));
  }

  @Test
  void list_anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/info-sections")).andExpect(status().is4xxClientError());
  }

  @Test
  void create_withoutManage_returns403() throws Exception {
    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_withManage_validBody_returns201() throws Exception {
    when(service.create(any())).thenReturn(view());

    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Portaria\",\"body\":\"<p>24h</p>\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Portaria"));
  }

  @Test
  void create_blankTitle_returns400() throws Exception {
    mvc.perform(
            post("/api/info-sections")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void update_notFound_returns404() throws Exception {
    when(service.update(eq(SID), any()))
        .thenThrow(new InfoException("NOT_FOUND", "Seção não encontrada."));

    mvc.perform(
            put("/api/info-sections/" + SID)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"body\":\"<p>b</p>\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_withManage_returns204() throws Exception {
    mvc.perform(delete("/api/info-sections/" + SID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
  }

  @Test
  void reorder_withManage_returns204() throws Exception {
    mvc.perform(
            put("/api/info-sections/reorder")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + SID + "\",\"position\":0}]}"))
        .andExpect(status().isNoContent());
  }
}
