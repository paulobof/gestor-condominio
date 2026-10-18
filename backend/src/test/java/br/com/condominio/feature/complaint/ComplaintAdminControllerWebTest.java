package br.com.condominio.feature.complaint;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.condominio.feature.complaint.dto.ComplaintAdminDetailView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ComplaintAdminController.class,
    properties = "app.feature.complaints.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ComplaintAdminControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID CID = UUID.randomUUID();
  private static final String PERM = "COMPLAINT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private ComplaintAdminService service;
  @MockBean private JwtService jwtService;

  private ComplaintAdminDetailView detail() {
    return new ComplaintAdminDetailView(
        ComplaintControllerWebTest.view(ComplaintStatus.IN_REVIEW), "Ana", "T2-51", List.of());
  }

  @Test
  void withoutPermission_is403() throws Exception {
    mvc.perform(get("/api/admin/complaints").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
  }

  @Test
  void queue_passesStatuses() throws Exception {
    when(service.queue(anySet(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
    mvc.perform(
            get("/api/admin/complaints")
                .param("status", "READY", "IN_REVIEW")
                .with(MockAuth.user(UID, PERM)))
        .andExpect(status().isOk());
    verify(service).queue(Set.of(ComplaintStatus.READY, ComplaintStatus.IN_REVIEW), 0, 20);
  }

  @Test
  void detail_includesAuthorAndUnit() throws Exception {
    when(service.detail(CID)).thenReturn(detail());
    mvc.perform(get("/api/admin/complaints/{id}", CID).with(MockAuth.user(UID, PERM)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorName").value("Ana"))
        .andExpect(jsonPath("$.unitCode").value("T2-51"));
  }

  @Test
  void startReview_invalidState_is409() throws Exception {
    when(service.startReview(CID, UID)).thenThrow(new ComplaintException("INVALID_STATE", "x"));
    mvc.perform(post("/api/admin/complaints/{id}/start-review", CID).with(MockAuth.user(UID, PERM)))
        .andExpect(status().isConflict());
  }

  @Test
  void reject_blankReason_is400() throws Exception {
    mvc.perform(
            post("/api/admin/complaints/{id}/reject", CID)
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void dueDate_past_is400WithCode() throws Exception {
    when(service.setDueDate(eq(CID), eq(UID), any()))
        .thenThrow(new ComplaintException("DUE_DATE_PAST", "passado"));
    mvc.perform(
            put("/api/admin/complaints/{id}/due-date", CID)
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueDate\":\"2020-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DUE_DATE_PAST"));
  }

  @Test
  void dueDate_passesParsedDate() throws Exception {
    when(service.setDueDate(CID, UID, LocalDate.of(2026, 11, 30))).thenReturn(detail());
    mvc.perform(
            put("/api/admin/complaints/{id}/due-date", CID)
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueDate\":\"2026-11-30\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void resolve_withoutBody_isAccepted() throws Exception {
    when(service.resolve(CID, UID, null)).thenReturn(detail());
    mvc.perform(post("/api/admin/complaints/{id}/resolve", CID).with(MockAuth.user(UID, PERM)))
        .andExpect(status().isOk());
  }

  @Test
  void response_passesText() throws Exception {
    when(service.setSyndicResponse(CID, UID, "Pintor vem dia 30.")).thenReturn(detail());
    mvc.perform(
            put("/api/admin/complaints/{id}/response", CID)
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Pintor vem dia 30.\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void response_withoutPermission_is403() throws Exception {
    mvc.perform(
            put("/api/admin/complaints/{id}/response", CID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}"))
        .andExpect(status().isForbidden());
  }
}
