package br.com.condominio.feature.complaint.batch;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.condominio.feature.complaint.ComplaintException;
import br.com.condominio.feature.complaint.batch.dto.BatchPreview;
import br.com.condominio.feature.complaint.batch.dto.ComplaintBatchView;
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

@WebMvcTest(controllers = ComplaintBatchController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ComplaintBatchControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final String PERM = "COMPLAINT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private ComplaintBatchService service;
  @MockBean private JwtService jwtService;

  @Test
  void withoutPermission_is403() throws Exception {
    mvc.perform(get("/api/admin/complaints/batches").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
  }

  @Test
  void preview_emptyIds_is400() throws Exception {
    mvc.perform(
            post("/api/admin/complaints/batches/preview")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void preview_returnsText() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.preview(List.of(id))).thenReturn(new BatchPreview("Reclamações — X", 1));
    mvc.perform(
            post("/api/admin/complaints/batches/preview")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Reclamações — X"));
  }

  @Test
  void create_returns201_andPassesActor() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.create(List.of(id), null, UID))
        .thenReturn(new ComplaintBatchView(UUID.randomUUID(), Instant.now(), "Ana", 1, "t"));
    mvc.perform(
            post("/api/admin/complaints/batches")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.complaintCount").value(1));
  }

  @Test
  void create_conflict_is409() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.create(List.of(id), null, UID))
        .thenThrow(new ComplaintException("INVALID_STATE", "Recarregue."));
    mvc.perform(
            post("/api/admin/complaints/batches")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void create_passesExpectedText_andStaleIs409() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.create(List.of(id), "texto copiado", UID))
        .thenThrow(new ComplaintException("BATCH_STALE", "Gere o texto de novo."));
    mvc.perform(
            post("/api/admin/complaints/batches")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"],\"expectedText\":\"texto copiado\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BATCH_STALE"));
  }
}
