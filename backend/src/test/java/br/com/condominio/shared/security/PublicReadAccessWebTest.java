package br.com.condominio.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.announcement.AnnouncementController;
import br.com.condominio.feature.announcement.AnnouncementService;
import br.com.condominio.feature.classified.ClassifiedController;
import br.com.condominio.feature.classified.ClassifiedService;
import br.com.condominio.feature.recommendation.RecommendationController;
import br.com.condominio.feature.recommendation.RecommendationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fronteira leitura-publica x escrita-autenticada, decidida pelo controlador dos dados em
 * 2026-08-25: visitante lê o conteúdo do condomínio sem sessão, mas publicar, votar e comentar
 * seguem exigindo conta.
 *
 * <p>Este teste existe para que reabrir/fechar uma área seja uma decisão explícita: qualquer
 * mudança em {@link SecurityConfig} que vaze escrita para anônimo quebra aqui.
 */
@WebMvcTest(
    controllers = {
      AnnouncementController.class,
      RecommendationController.class,
      ClassifiedController.class
    })
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
@org.springframework.test.context.TestPropertySource(
    properties = {
      "app.feature.announcements.enabled=true",
      "app.feature.recommendations.enabled=true",
      "app.feature.classifieds.enabled=true"
    })
class PublicReadAccessWebTest {

  @Autowired private MockMvc mvc;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter
  @MockBean private AnnouncementService announcementService;
  @MockBean private RecommendationService recommendationService;
  @MockBean private ClassifiedService classifiedService;

  // ===== leitura: aberta ao visitante =====

  @Test
  void anonimo_leAvisos() throws Exception {
    Page<?> vazio = new PageImpl<>(List.of());
    org.mockito.Mockito.when(announcementService.list(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> vazio);
    mvc.perform(get("/api/announcements")).andExpect(status().isOk());
  }

  @Test
  void anonimo_leIndicacoes() throws Exception {
    Page<?> vazio = new PageImpl<>(List.of());
    org.mockito.Mockito.when(
            recommendationService.list(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> vazio);
    mvc.perform(get("/api/recommendations")).andExpect(status().isOk());
  }

  @Test
  void anonimo_leComentariosDaIndicacao() throws Exception {
    org.mockito.Mockito.when(recommendationService.listComments(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    mvc.perform(get("/api/recommendations/" + UUID.randomUUID() + "/comments"))
        .andExpect(status().isOk());
  }

  @Test
  void anonimo_leClassificados() throws Exception {
    Page<?> vazio = new PageImpl<>(List.of());
    org.mockito.Mockito.when(
            classifiedService.list(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> vazio);
    mvc.perform(get("/api/classifieds")).andExpect(status().isOk());
  }

  // ===== escrita: continua exigindo sessão =====

  @Test
  void anonimo_naoPublicaIndicacao() throws Exception {
    mvc.perform(
            post("/api/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"serviceName\":\"x\",\"professionalName\":\"y\",\"phone\":\"11999998888\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonimo_naoVota() throws Exception {
    mvc.perform(
            post("/api/recommendations/" + UUID.randomUUID() + "/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"LIKE\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonimo_naoComenta() throws Exception {
    mvc.perform(
            post("/api/recommendations/" + UUID.randomUUID() + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"oi\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonimo_naoPublicaClassificado() throws Exception {
    mvc.perform(
            post("/api/classifieds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"description\":\"y\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anonimo_naoCriaAviso() throws Exception {
    mvc.perform(
            post("/api/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"body\":\"y\",\"importance\":\"HIGH\"}"))
        .andExpect(status().isUnauthorized());
  }
}
