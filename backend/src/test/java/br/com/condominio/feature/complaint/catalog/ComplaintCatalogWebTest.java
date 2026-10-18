package br.com.condominio.feature.complaint.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.condominio.feature.complaint.catalog.dto.*;
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

@WebMvcTest(controllers = {ComplaintCatalogController.class, ComplaintCatalogAdminController.class})
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ComplaintCatalogWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final String PERM = "COMPLAINT_CATALOG_MANAGE";
  private static final String BODY = "{\"name\":\"Limpeza\",\"sortOrder\":1,\"active\":true}";

  @Autowired private MockMvc mvc;
  @MockBean private ComplaintCatalogService service;
  @MockBean private JwtService jwtService;

  @Test
  void publicCatalog_anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/complaints/catalog")).andExpect(status().is4xxClientError());
  }

  @Test
  void publicCatalog_returnsActiveOnly() throws Exception {
    when(service.catalog(false)).thenReturn(new CatalogView(List.of(), List.of()));
    mvc.perform(get("/api/complaints/catalog").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.types").isArray());
  }

  @Test
  void adminCatalog_withoutPermission_is403() throws Exception {
    mvc.perform(get("/api/admin/complaint-catalog").with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCatalog_includesInactive() throws Exception {
    when(service.catalog(true)).thenReturn(new CatalogView(List.of(), List.of()));
    mvc.perform(get("/api/admin/complaint-catalog").with(MockAuth.user(UID, PERM)))
        .andExpect(status().isOk());
  }

  @Test
  void createType_blankName_is400() throws Exception {
    mvc.perform(
            post("/api/admin/complaint-catalog/types")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"sortOrder\":1,\"active\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createType_returns201() throws Exception {
    when(service.createType(any()))
        .thenReturn(new CatalogTypeView(UUID.randomUUID(), "Limpeza", true, 1, List.of()));
    mvc.perform(
            post("/api/admin/complaint-catalog/types")
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Limpeza"));
  }

  @Test
  void createSubtype_passesTypeId() throws Exception {
    UUID typeId = UUID.randomUUID();
    when(service.createSubtype(eq(typeId), any()))
        .thenReturn(new CatalogItemView(UUID.randomUUID(), "Pintura", true, 1));
    mvc.perform(
            post("/api/admin/complaint-catalog/types/{id}/subtypes", typeId)
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated());
  }

  @Test
  void move_withoutPermission_is403() throws Exception {
    mvc.perform(
            post("/api/admin/complaint-catalog/types/{id}/move", UUID.randomUUID())
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"direction\":\"UP\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void move_eachKind_returns204() throws Exception {
    UUID id = UUID.randomUUID();
    for (String kind : List.of("types", "subtypes", "locations")) {
      mvc.perform(
              post("/api/admin/complaint-catalog/{kind}/{id}/move", kind, id)
                  .with(MockAuth.user(UID, PERM))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"direction\":\"DOWN\"}"))
          .andExpect(status().isNoContent());
    }
    verify(service).moveType(id, MoveDirection.DOWN);
    verify(service).moveSubtype(id, MoveDirection.DOWN);
    verify(service).moveLocation(id, MoveDirection.DOWN);
  }

  @Test
  void move_missingDirection_is400() throws Exception {
    mvc.perform(
            post("/api/admin/complaint-catalog/types/{id}/move", UUID.randomUUID())
                .with(MockAuth.user(UID, PERM))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
