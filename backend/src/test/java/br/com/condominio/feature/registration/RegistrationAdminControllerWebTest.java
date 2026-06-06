package br.com.condominio.feature.registration;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.audit.AuditWriter;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP do {@link RegistrationAdminController}: matriz de permissões (REGISTRATION_VIEW p/
 * listar, REGISTRATION_APPROVE p/ aprovar/rejeitar, RESIDENCE_PROOF_VIEW p/ URL do comprovante) —
 * 403 sem a permission — e trilha de auditoria nos acessos sensíveis.
 */
@WebMvcTest(controllers = RegistrationAdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class RegistrationAdminControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID REG = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private RegistrationService service;
  @MockBean private AuditWriter auditWriter;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  // ---- listPending: REGISTRATION_VIEW ---------------------------------------------

  @Test
  void listPending_withRegistrationView_returns200_andAudits() throws Exception {
    when(service.listPending(any())).thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/api/registrations").with(MockAuth.user(UID, "REGISTRATION_VIEW")))
        .andExpect(status().isOk());

    verify(auditWriter).logSensitiveAccess(any(), any(), any(), any());
  }

  @Test
  void listPending_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/registrations").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).listPending(any());
  }

  // ---- approve / reject: REGISTRATION_APPROVE -------------------------------------

  @Test
  void approve_withApprove_returns204() throws Exception {
    mvc.perform(
            post("/api/registrations/{id}/approve", REG)
                .with(MockAuth.user(UID, "REGISTRATION_APPROVE")))
        .andExpect(status().isNoContent());
    verify(service).approve(REG, UID);
  }

  @Test
  void approve_withoutPermission_returns403() throws Exception {
    mvc.perform(post("/api/registrations/{id}/approve", REG).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).approve(any(), any());
  }

  @Test
  void reject_withApprove_returns204() throws Exception {
    mvc.perform(
            post("/api/registrations/{id}/reject", REG)
                .with(MockAuth.user(UID, "REGISTRATION_APPROVE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"comprovante ilegível\"}"))
        .andExpect(status().isNoContent());
    verify(service).reject(REG, UID, "comprovante ilegível");
  }

  @Test
  void reject_blankReason_returns400() throws Exception {
    mvc.perform(
            post("/api/registrations/{id}/reject", REG)
                .with(MockAuth.user(UID, "REGISTRATION_APPROVE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).reject(any(), any(), any());
  }

  // ---- proofUrl: RESIDENCE_PROOF_VIEW ---------------------------------------------

  @Test
  void proofUrl_withProofView_returns200_noReferrer_andAudits() throws Exception {
    when(service.getProofPresignedUrl(REG)).thenReturn("https://minio/proof-signed");

    mvc.perform(
            get("/api/registrations/{id}/proof-url", REG)
                .with(MockAuth.user(UID, "RESIDENCE_PROOF_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://minio/proof-signed"))
        .andExpect(jsonPath("$.ttlSeconds").value(300))
        .andExpect(header().string("Referrer-Policy", containsString("no-referrer")));

    verify(auditWriter).logProofAccess(any(), any(), any(), anyInt());
  }

  @Test
  void proofUrl_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/registrations/{id}/proof-url", REG).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).getProofPresignedUrl(any());
  }
}
