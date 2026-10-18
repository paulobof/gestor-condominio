package br.com.condominio.feature.complaint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.condominio.feature.complaint.dto.ComplaintView;
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

@WebMvcTest(
    controllers = ComplaintController.class,
    properties = "app.feature.complaints.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ComplaintControllerWebTest {

  static final UUID UID = UUID.randomUUID();
  static final UUID UNIT = UUID.randomUUID();
  static final UUID CID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private ComplaintService service;
  @MockBean private JwtService jwtService;

  static ComplaintView view(ComplaintStatus status) {
    return new ComplaintView(
        CID,
        123L,
        UUID.randomUUID(),
        "Manutenção",
        UUID.randomUUID(),
        "Pintura",
        UUID.randomUUID(),
        "Torre 2",
        "hall",
        "Parede descascando",
        status,
        null,
        null,
        null,
        Instant.now(),
        null,
        null,
        true,
        List.of());
  }

  private String body() {
    return """
        {"typeId":"%s","subtypeId":"%s","locationId":"%s","locationDetail":"hall",
         "description":"Parede descascando no hall"}
        """
        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }

  @Test
  void create_returns201_andPassesPrincipalUnit() throws Exception {
    when(service.create(eq(UID), eq(UNIT), any())).thenReturn(view(ComplaintStatus.OPEN));
    mvc.perform(
            post("/api/complaints")
                .with(MockAuth.userWithUnit(UID, UNIT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(123))
        .andExpect(jsonPath("$.authorUserId").doesNotExist());
  }

  @Test
  void create_missingType_is400() throws Exception {
    mvc.perform(
            post("/api/complaints")
                .with(MockAuth.userWithUnit(UID, UNIT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Parede descascando no hall\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_withoutUnit_is403() throws Exception {
    when(service.create(eq(UID), eq(null), any()))
        .thenThrow(new ComplaintException("NO_UNIT", "sem unidade"));
    mvc.perform(
            post("/api/complaints")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_UNIT"));
  }

  @Test
  void anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/complaints/mine")).andExpect(status().is4xxClientError());
  }

  @Test
  void mine_returnsList() throws Exception {
    when(service.mine(UID)).thenReturn(List.of(view(ComplaintStatus.REJECTED)));
    mvc.perform(get("/api/complaints/mine").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("REJECTED"));
  }

  @Test
  void get_passesCanManage() throws Exception {
    when(service.get(CID, UID, true)).thenReturn(view(ComplaintStatus.OPEN));
    mvc.perform(get("/api/complaints/{id}", CID).with(MockAuth.user(UID, "COMPLAINT_MANAGE")))
        .andExpect(status().isOk());
    verify(service).get(CID, UID, true);
  }

  @Test
  void update_invalidState_is409() throws Exception {
    when(service.update(eq(CID), eq(UID), any()))
        .thenThrow(new ComplaintException("INVALID_STATE", "x"));
    mvc.perform(
            put("/api/complaints/{id}", CID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isConflict());
  }

  @Test
  void cancel_returns204() throws Exception {
    mvc.perform(delete("/api/complaints/{id}", CID).with(MockAuth.user(UID)))
        .andExpect(status().isNoContent());
    verify(service).cancel(CID, UID);
  }
}
