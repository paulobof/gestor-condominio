package br.com.condominio.feature.tag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.tag.dto.TagView;
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
 * Contrato HTTP do {@link TagController}: feature flag ligada, {@code isAuthenticated()} no
 * autocomplete/criação e {@code TAG_MANAGE} obrigatório no delete (Plano 3B, Task 12 Step 3.7).
 */
@WebMvcTest(
    controllers = TagController.class,
    properties = "app.feature.recommendations.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class TagControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private TagService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  @Test
  void autocomplete_returns200() throws Exception {
    when(service.searchForAutocomplete("en"))
        .thenReturn(List.of(new TagView(UUID.randomUUID(), "encanador", "Encanador", null)));

    mvc.perform(get("/api/tags").param("q", "en").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug").value("encanador"));
  }

  @Test
  void autocomplete_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/tags")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  @Test
  void create_returns201() throws Exception {
    when(service.getOrCreate("encanador", "Encanador"))
        .thenReturn(Tag.create("encanador", "Encanador", null));

    mvc.perform(
            post("/api/tags")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"encanador\",\"label\":\"Encanador\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("encanador"));
  }

  @Test
  void create_blankSlug_returns400() throws Exception {
    mvc.perform(
            post("/api/tags")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).getOrCreate(any(), any());
  }

  @Test
  void delete_withoutTagManage_returns403() throws Exception {
    mvc.perform(delete("/api/tags/{id}", UUID.randomUUID()).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).delete(any());
  }

  @Test
  void delete_withTagManage_returns204() throws Exception {
    UUID tagId = UUID.randomUUID();
    mvc.perform(delete("/api/tags/{id}", tagId).with(MockAuth.user(UID, "TAG_MANAGE")))
        .andExpect(status().isNoContent());
    verify(service).delete(eq(tagId));
  }
}
