package br.com.condominio.feature.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.audit.AuditWriter;
import br.com.condominio.feature.unit.dto.OwnershipClaimView;
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

@WebMvcTest(
    controllers = OwnershipAdminController.class,
    properties = "app.feature.unitownership.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class OwnershipAdminControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID CID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private UnitOwnershipService service;
  @MockBean private AuditWriter auditWriter;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private OwnershipClaimView claim() {
    return new OwnershipClaimView(
        CID,
        UID,
        "Ana Costa",
        UUID.randomUUID(),
        "702C",
        "comprovante.pdf",
        Instant.now(),
        Instant.now());
  }

  @Test
  void list_withRegistrationView_returns200() throws Exception {
    when(service.listPendingClaims(any()))
        .thenReturn(new PageImpl<>(List.of(claim()), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/ownership-claims").with(MockAuth.user(UID, "REGISTRATION_VIEW")))
        .andExpect(status().isOk());
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/ownership-claims")).andExpect(status().is4xxClientError());
  }

  @Test
  void list_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/ownership-claims").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
  }

  @Test
  void approve_withApprovePermission_returns204() throws Exception {
    mvc.perform(
            post("/api/ownership-claims/{id}/approve", CID)
                .with(MockAuth.user(UID, "REGISTRATION_APPROVE")))
        .andExpect(status().isNoContent());
    verify(service).approve(CID, UID);
  }

  @Test
  void reject_withApprovePermission_returns204() throws Exception {
    mvc.perform(
            post("/api/ownership-claims/{id}/reject", CID)
                .with(MockAuth.user(UID, "REGISTRATION_APPROVE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"comprovante ilegível\"}"))
        .andExpect(status().isNoContent());
    verify(service).reject(eq(CID), eq(UID), any());
  }

  @Test
  void proof_withProofView_returnsBytes() throws Exception {
    when(service.getClaimProofContent(CID))
        .thenReturn(
            new UnitOwnershipService.ProofContent(
                new byte[] {1, 2, 3}, "application/pdf", "comprovante.pdf"));
    mvc.perform(
            get("/api/ownership-claims/{id}/proof", CID)
                .with(MockAuth.user(UID, "RESIDENCE_PROOF_VIEW")))
        .andExpect(status().isOk());
  }

  @Test
  void approve_withoutPermission_returns403() throws Exception {
    mvc.perform(post("/api/ownership-claims/{id}/approve", CID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
  }
}
