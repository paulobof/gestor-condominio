package br.com.condominio.feature.classified;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.math.BigDecimal;
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
 * Contrato HTTP do {@link ClassifiedController} (3A). Diferente do 3B, a moderação não é por
 * {@code @PreAuthorize} e sim autor-ou-moderador no service — então o foco é a propagação de {@code
 * canModerate}, a feature flag, validação e o mapeamento exceção→status.
 */
@WebMvcTest(
    controllers = ClassifiedController.class,
    properties = "app.feature.classifieds.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ClassifiedControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID CID = UUID.randomUUID();
  private static final String MODERATE = "CLASSIFIED_MODERATE";

  @Autowired private MockMvc mvc;
  @MockBean private ClassifiedService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private ClassifiedView view() {
    return new ClassifiedView(
        CID,
        "Sofá 3 lugares",
        "seminovo",
        new BigDecimal("500.00"),
        ClassifiedStatus.ACTIVE,
        UID,
        Instant.now(),
        List.of());
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list(any(), any()))
        .thenReturn(new PageImpl<>(List.of(view()), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/classifieds").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Sofá 3 lugares"))
        .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/classifieds")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  @Test
  void get_returns200() throws Exception {
    when(service.getById(CID)).thenReturn(view());
    mvc.perform(get("/api/classifieds/{id}", CID).with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(CID.toString()));
  }

  @Test
  void create_returns201() throws Exception {
    when(service.create(eq(UID), any())).thenReturn(view());

    mvc.perform(
            post("/api/classifieds")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sofá 3 lugares\",\"price\":500.00}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void create_blankTitle_returns400() throws Exception {
    mvc.perform(
            post("/api/classifieds")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).create(any(), any());
  }

  @Test
  void update_passesCanModerateFalse_whenNotModerator() throws Exception {
    when(service.update(eq(CID), eq(UID), eq(false), any())).thenReturn(view());

    mvc.perform(
            put("/api/classifieds/{id}", CID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sofá\"}"))
        .andExpect(status().isOk());

    verify(service).update(eq(CID), eq(UID), eq(false), any());
  }

  @Test
  void update_passesCanModerateTrue_whenModerator() throws Exception {
    when(service.update(eq(CID), eq(UID), eq(true), any())).thenReturn(view());

    mvc.perform(
            put("/api/classifieds/{id}", CID)
                .with(MockAuth.user(UID, MODERATE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sofá\"}"))
        .andExpect(status().isOk());

    verify(service).update(eq(CID), eq(UID), eq(true), any());
  }

  @Test
  void delete_returns204_andPassesCanModerate() throws Exception {
    mvc.perform(delete("/api/classifieds/{id}", CID).with(MockAuth.user(UID)))
        .andExpect(status().isNoContent());
    verify(service).delete(CID, UID, false);
  }

  @Test
  void notFoundException_mapsTo404() throws Exception {
    when(service.getById(CID)).thenThrow(new ClassifiedException("NOT_FOUND", "não encontrado"));
    mvc.perform(get("/api/classifieds/{id}", CID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forbiddenException_mapsTo403() throws Exception {
    when(service.update(eq(CID), eq(UID), eq(false), any()))
        .thenThrow(new ClassifiedException("FORBIDDEN", "não é o autor"));
    mvc.perform(
            put("/api/classifieds/{id}", CID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sofá\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void addPhoto_returns201() throws Exception {
    UUID photoId = UUID.randomUUID();
    when(service.addPhoto(eq(CID), eq(UID), eq(false), any()))
        .thenReturn(new ClassifiedPhotoView(photoId, 0, "image/jpeg"));

    MockMultipartFile file =
        new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});

    mvc.perform(multipart("/api/classifieds/{id}/photos", CID).file(file).with(MockAuth.user(UID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("image/jpeg"));
  }

  @Test
  void addPhoto_rejectedByService_mapsTo400() throws Exception {
    when(service.addPhoto(eq(CID), eq(UID), eq(false), any()))
        .thenThrow(new ClassifiedException("PHOTO_INVALID_TYPE", "PDF não permitido"));

    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

    mvc.perform(multipart("/api/classifieds/{id}/photos", CID).file(file).with(MockAuth.user(UID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PHOTO_INVALID_TYPE"));
  }
}
