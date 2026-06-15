package br.com.condominio.feature.contact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.contact.dto.ContactView;
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

/**
 * Contrato HTTP do {@link ContactController}: feature flag; leitura para autenticados; escrita
 * exige {@code CONTACT_MANAGE} (403 sem); validação de entrada.
 */
@WebMvcTest(controllers = ContactController.class, properties = "app.feature.contacts.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ContactControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID CID = UUID.randomUUID();
  private static final String MANAGE = "CONTACT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private ContactService service;
  @MockBean private JwtService jwtService;

  private ContactView view() {
    return new ContactView(
        CID, "Portaria", "Condomínio", "+551133334444", "", true, List.of(), Instant.now());
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list()).thenReturn(List.of(view()));

    mvc.perform(get("/api/contacts").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Portaria"));
  }

  @Test
  void create_withoutManage_returns403() throws Exception {
    mvc.perform(
            post("/api/contacts")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Portaria\",\"category\":\"Condomínio\",\"phone\":\"+551133334444\","
                        + "\"notes\":\"\",\"is24h\":true,\"openingHours\":[]}"))
        .andExpect(status().isForbidden());
    verify(service, never()).create(any());
  }

  @Test
  void create_withManage_returns201() throws Exception {
    when(service.create(any())).thenReturn(view());

    mvc.perform(
            post("/api/contacts")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Portaria\",\"category\":\"Condomínio\",\"phone\":\"+551133334444\","
                        + "\"notes\":\"\",\"is24h\":true,\"openingHours\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Portaria"));
  }

  @Test
  void create_blankName_returns400() throws Exception {
    mvc.perform(
            post("/api/contacts")
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"\",\"category\":\"Condomínio\",\"phone\":\"+551133334444\","
                        + "\"notes\":\"\",\"is24h\":true,\"openingHours\":[]}"))
        .andExpect(status().isBadRequest());
    verify(service, never()).create(any());
  }

  @Test
  void delete_withManage_notFound_returns404() throws Exception {
    doThrow(new ContactException("NOT_FOUND", "Contato não encontrado."))
        .when(service)
        .delete(any());

    mvc.perform(delete("/api/contacts/{id}", CID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void list_anonymous_isRejected() throws Exception {
    mvc.perform(get("/api/contacts")).andExpect(status().is4xxClientError());
  }

  @Test
  void update_withoutManage_returns403() throws Exception {
    mvc.perform(
            put("/api/contacts/{id}", CID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isForbidden());
    verify(service, never()).update(any(), any());
  }

  @Test
  void update_withManage_notFound_returns404() throws Exception {
    when(service.update(any(), any()))
        .thenThrow(new ContactException("NOT_FOUND", "Contato não encontrado."));

    mvc.perform(
            put("/api/contacts/{id}", CID)
                .with(MockAuth.user(UID, MANAGE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isNotFound());
  }

  private static String validBody() {
    return "{\"name\":\"Portaria\",\"category\":\"Condomínio\",\"phone\":\"+551133334444\","
        + "\"notes\":\"\",\"is24h\":true,\"openingHours\":[]}";
  }
}
