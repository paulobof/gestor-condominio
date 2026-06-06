package br.com.condominio.feature.privacy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.privacy.dto.PersonalDataExportResponse;
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
 * Contrato HTTP do {@link PrivacyController} (LGPD self-service): export/atividades exigem
 * autenticação; anonimização confirma dupla (senha + "ANONIMIZAR") e mapeia INVALID_PASSWORD→401.
 */
@WebMvcTest(controllers = PrivacyController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class PrivacyControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private PrivacyService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private PersonalDataExportResponse export() {
    return new PersonalDataExportResponse(
        UID,
        null,
        null,
        List.of("paulo@test.com"),
        null,
        null,
        null,
        null,
        "ACTIVE",
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        List.of("RESIDENT"),
        null);
  }

  @Test
  void exportSelf_authenticated_returns200() throws Exception {
    when(service.exportSelf(UID)).thenReturn(export());

    mvc.perform(get("/api/privacy/me/export").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(UID.toString()));
  }

  @Test
  void exportSelf_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/privacy/me/export")).andExpect(status().is4xxClientError());
    verify(service, never()).exportSelf(any());
  }

  @Test
  void processingActivities_authenticated_returns200Array() throws Exception {
    when(service.processingActivities()).thenReturn(List.of());
    mvc.perform(get("/api/privacy/me/processing-activities").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void anonymize_returns204_andDelegates() throws Exception {
    mvc.perform(
            post("/api/privacy/me/anonymize")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"senha123\",\"confirmText\":\"ANONIMIZAR\"}"))
        .andExpect(status().isNoContent());
    verify(service).anonymizeSelf(eq(UID), eq("senha123"));
  }

  @Test
  void anonymize_wrongConfirmText_returns400() throws Exception {
    mvc.perform(
            post("/api/privacy/me/anonymize")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"senha123\",\"confirmText\":\"sim\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).anonymizeSelf(any(), any());
  }

  @Test
  void anonymize_invalidPassword_returns401() throws Exception {
    doThrow(new PrivacyException("INVALID_PASSWORD", "senha incorreta"))
        .when(service)
        .anonymizeSelf(any(), any());

    mvc.perform(
            post("/api/privacy/me/anonymize")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"errada\",\"confirmText\":\"ANONIMIZAR\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
  }
}
