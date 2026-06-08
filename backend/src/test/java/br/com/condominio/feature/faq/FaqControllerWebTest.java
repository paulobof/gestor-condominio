package br.com.condominio.feature.faq;

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

import br.com.condominio.feature.faq.dto.FaqView;
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

/**
 * Contrato HTTP do {@link FaqController}: feature flag; leitura para autenticados; escrita exige
 * {@code FAQ_MANAGE} (403 sem); validação de entrada.
 */
@WebMvcTest(controllers = FaqController.class, properties = "app.feature.faq.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class FaqControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID FID = UUID.randomUUID();
  private static final String MANAGE = "FAQ_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private FaqService service;
  @MockBean private JwtService jwtService;

  private FaqView view() {
    return new FaqView(
        FID, "O que é o condomínio?", "É um ótimo lugar.", "Geral", true, 1, Instant.now());
  }

  @Test
  void listPublished_authenticated_returns200() throws Exception {
    when(service.listPublished()).thenReturn(List.of(view()));

    mvc.perform(get("/api/faq").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].question").value("O que é o condomínio?"));
  }

  @Test
  void create_withoutManage_returns403() throws Exception {
    mvc.perform(
            post("/api/faq")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"question\":\"Pergunta?\",\"answer\":\"Resposta.\",\"category\":\"Geral\",\"published\":false}"))
        .andExpect(status().isForbidden());
    verify(service, never()).create(any());
  }

  @Test
  void create_withManage_returns201() throws Exception {
    when(service.create(any())).thenReturn(view());

    mvc.perform(
            post("/api/faq")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"question\":\"Pergunta?\",\"answer\":\"Resposta.\",\"category\":\"Geral\",\"published\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.question").value("O que é o condomínio?"));
  }

  @Test
  void create_blankQuestion_returns400() throws Exception {
    mvc.perform(
            post("/api/faq")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"question\":\"\",\"answer\":\"Resposta.\",\"category\":\"Geral\",\"published\":false}"))
        .andExpect(status().isBadRequest());
    verify(service, never()).create(any());
  }

  @Test
  void publish_withManage_returns200() throws Exception {
    when(service.setPublished(eq(FID), eq(true))).thenReturn(view());

    mvc.perform(
            put("/api/faq/{id}/publish", FID)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.published").value(true));
  }

  @Test
  void reorder_withManage_returns204() throws Exception {
    mvc.perform(
            put("/api/faq/reorder")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"" + FID + "\",\"ordering\":1}]}"))
        .andExpect(status().isNoContent());
    verify(service).reorder(any());
  }

  @Test
  void delete_withManage_returns204() throws Exception {
    mvc.perform(delete("/api/faq/{id}", FID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).delete(FID);
  }

  @Test
  void listPublished_anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/faq")).andExpect(status().is4xxClientError());
  }

  @Test
  void delete_notFound_returns404() throws Exception {
    doThrow(new FaqException("NOT_FOUND", "FAQ não encontrado.")).when(service).delete(any());
    mvc.perform(delete("/api/faq/{id}", FID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNotFound());
  }
}
