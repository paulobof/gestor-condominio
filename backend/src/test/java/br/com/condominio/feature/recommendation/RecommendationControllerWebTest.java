package br.com.condominio.feature.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.recommendation.dto.RecommendationPhotoView;
import br.com.condominio.feature.recommendation.dto.RecommendationView;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link RecommendationController} (equivalente automatizado do checklist e2e do
 * Plano 3B, Task 12 Step 3): feature flag ligada, matriz de autorização {@code @PreAuthorize},
 * propagação de {@code canModerate} (proteção de PII), serialização e mapeamento exceção→status.
 */
@WebMvcTest(
    controllers = RecommendationController.class,
    properties = "app.feature.recommendations.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RecommendationControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID RID = UUID.randomUUID();
  private static final String MODERATE = "RECOMMENDATION_MODERATE";

  @Autowired private MockMvc mvc;
  @MockBean private RecommendationService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private RecommendationView view(RecommendationStatus status) {
    return new RecommendationView(
        RID,
        "Encanador Zé",
        "Zé",
        "11999998888",
        false,
        null,
        null,
        null,
        5,
        "ótimo",
        UID,
        status,
        Instant.now(),
        List.of(),
        List.of(),
        List.of());
  }

  // ---- flag + autenticação básica -------------------------------------------------

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list(any(), eq(false), any(), any()))
        .thenReturn(
            new PageImpl<>(List.of(view(RecommendationStatus.ACTIVE)), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/recommendations").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].serviceName").value("Encanador Zé"))
        .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/recommendations")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  // ---- proteção de PII: canModerate propagado ao service --------------------------

  @Test
  void get_passesCanModerateFalse_whenNotModerator() throws Exception {
    when(service.getById(eq(RID), eq(UID), eq(false)))
        .thenReturn(view(RecommendationStatus.ACTIVE));

    mvc.perform(get("/api/recommendations/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isOk());

    verify(service).getById(RID, UID, false);
  }

  @Test
  void get_passesCanModerateTrue_whenModerator() throws Exception {
    when(service.getById(eq(RID), eq(UID), eq(true))).thenReturn(view(RecommendationStatus.HIDDEN));

    mvc.perform(get("/api/recommendations/{id}", RID).with(MockAuth.user(UID, MODERATE)))
        .andExpect(status().isOk());

    verify(service).getById(RID, UID, true);
  }

  // ---- create + validação ---------------------------------------------------------

  @Test
  void create_returns201() throws Exception {
    when(service.create(eq(UID), any())).thenReturn(view(RecommendationStatus.ACTIVE));

    mvc.perform(
            post("/api/recommendations")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceName\":\"Encanador Zé\",\"isResident\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void create_blankServiceName_returns400Validation() throws Exception {
    mvc.perform(
            post("/api/recommendations")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceName\":\"\",\"isResident\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).create(any(), any());
  }

  // ---- matriz de autorização: hide exige RECOMMENDATION_MODERATE ------------------

  @Test
  void hide_withoutModerate_returns403() throws Exception {
    mvc.perform(post("/api/recommendations/{id}/hide", RID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).hide(any());
  }

  @Test
  void hide_withModerate_returns204() throws Exception {
    mvc.perform(post("/api/recommendations/{id}/hide", RID).with(MockAuth.user(UID, MODERATE)))
        .andExpect(status().isNoContent());
    verify(service).hide(RID);
  }

  // ---- consentimento delega flag approved -----------------------------------------

  @Test
  void residentConsent_returns204_andDelegatesApproved() throws Exception {
    mvc.perform(
            post("/api/recommendations/{id}/resident-consent", RID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}"))
        .andExpect(status().isNoContent());
    verify(service).residentConsent(RID, UID, false, true);
  }

  // ---- mapeamento exceção→status --------------------------------------------------

  @Test
  void notFoundException_mapsTo404() throws Exception {
    when(service.getById(eq(RID), eq(UID), eq(false)))
        .thenThrow(new RecommendationException("NOT_FOUND", "não encontrada"));

    mvc.perform(get("/api/recommendations/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forbiddenException_mapsTo403() throws Exception {
    when(service.getById(eq(RID), eq(UID), eq(false)))
        .thenThrow(new RecommendationException("FORBIDDEN", "sem acesso"));

    mvc.perform(get("/api/recommendations/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  // ---- upload de foto (multipart) -------------------------------------------------

  @Test
  void addPhoto_returns201() throws Exception {
    UUID photoId = UUID.randomUUID();
    when(service.addPhoto(eq(RID), eq(UID), eq(false), any()))
        .thenReturn(new RecommendationPhotoView(photoId, 0, "image/jpeg"));

    MockMultipartFile file =
        new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});

    mvc.perform(
            multipart("/api/recommendations/{id}/photos", RID).file(file).with(MockAuth.user(UID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("image/jpeg"));
  }

  @Test
  void addPhoto_rejectedByService_mapsTo400() throws Exception {
    when(service.addPhoto(eq(RID), eq(UID), eq(false), any()))
        .thenThrow(new RecommendationException("PHOTO_INVALID_TYPE", "PDF não permitido"));

    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

    mvc.perform(
            multipart("/api/recommendations/{id}/photos", RID).file(file).with(MockAuth.user(UID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PHOTO_INVALID_TYPE"));
  }
}
