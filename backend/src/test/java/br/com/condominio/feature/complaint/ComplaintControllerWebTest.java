package br.com.condominio.feature.complaint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.condominio.feature.complaint.dto.ComplaintPhotoView;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ComplaintController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ComplaintControllerWebTest {

  static final UUID UID = UUID.randomUUID();
  static final UUID UNIT = UUID.randomUUID();
  static final UUID CID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private ComplaintService service;
  @MockBean private ComplaintPhotoService photoService;
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
    when(service.get(CID, UID, null, true)).thenReturn(view(ComplaintStatus.OPEN));
    mvc.perform(get("/api/complaints/{id}", CID).with(MockAuth.user(UID, "COMPLAINT_MANAGE")))
        .andExpect(status().isOk());
    verify(service).get(CID, UID, null, true);
  }

  @Test
  void get_passesPrincipalUnit_andNoUnitIs403() throws Exception {
    UUID unit = UUID.randomUUID();
    when(service.get(CID, UID, unit, false)).thenReturn(view(ComplaintStatus.FORWARDED));
    mvc.perform(get("/api/complaints/{id}", CID).with(MockAuth.userWithUnit(UID, unit)))
        .andExpect(status().isOk());

    when(service.get(CID, UID, null, false))
        .thenThrow(new ComplaintException("NO_UNIT", "O mural é exclusivo para moradores."));
    mvc.perform(get("/api/complaints/{id}", CID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
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

  @Test
  void addPhoto_returns201() throws Exception {
    UUID pid = UUID.randomUUID();
    when(photoService.addPhoto(eq(CID), eq(UID), any()))
        .thenReturn(new ComplaintPhotoView(pid, 0, "image/jpeg"));
    mvc.perform(
            multipart("/api/complaints/{id}/photos", CID)
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1}))
                .with(MockAuth.user(UID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(pid.toString()));
  }

  @Test
  void addPhoto_limit_is400() throws Exception {
    when(photoService.addPhoto(eq(CID), eq(UID), any()))
        .thenThrow(new ComplaintException("PHOTO_LIMIT", "Máximo de 3 fotos por reclamação."));
    mvc.perform(
            multipart("/api/complaints/{id}/photos", CID)
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1}))
                .with(MockAuth.user(UID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PHOTO_LIMIT"));
  }

  @Test
  void photoUrl_setsNoReferrer() throws Exception {
    UUID pid = UUID.randomUUID();
    when(photoService.photoUrl(CID, pid, UID, null, false)).thenReturn("http://minio/x");
    mvc.perform(get("/api/complaints/{id}/photos/{pid}/url", CID, pid).with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(jsonPath("$.url").value("http://minio/x"));
  }

  @Test
  void removePhoto_returns204() throws Exception {
    UUID pid = UUID.randomUUID();
    mvc.perform(delete("/api/complaints/{id}/photos/{pid}", CID, pid).with(MockAuth.user(UID)))
        .andExpect(status().isNoContent());
    verify(photoService).removePhoto(CID, pid, UID);
  }

  @Test
  void board_passesFiltersAndUnit_andHidesAuthor() throws Exception {
    UUID typeId = UUID.randomUUID();
    when(service.board(UID, UNIT, false, typeId, null, ComplaintStatus.FORWARDED, 0, 20))
        .thenReturn(
            new org.springframework.data.domain.PageImpl<>(
                List.of(view(ComplaintStatus.FORWARDED))));
    mvc.perform(
            get("/api/complaints/board")
                .param("typeId", typeId.toString())
                .param("status", "FORWARDED")
                .with(MockAuth.userWithUnit(UID, UNIT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("FORWARDED"))
        .andExpect(jsonPath("$.content[0].authorUserId").doesNotExist())
        .andExpect(jsonPath("$.content[0].authorUnitId").doesNotExist());
  }

  @Test
  void board_invalidStatusParam_is400() throws Exception {
    mvc.perform(
            get("/api/complaints/board")
                .param("status", "XYZ")
                .with(MockAuth.userWithUnit(UID, UNIT)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
  }
}
