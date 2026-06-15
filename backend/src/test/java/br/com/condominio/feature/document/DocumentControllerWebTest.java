package br.com.condominio.feature.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.document.dto.DocumentView;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = DocumentController.class,
    properties = "app.feature.documents.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class DocumentControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID DID = UUID.randomUUID();
  private static final String MANAGE = "DOCUMENT_MANAGE";

  @Autowired private MockMvc mvc;
  @MockBean private DocumentService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private DocumentView view() {
    return new DocumentView(
        DID,
        "Regimento Interno",
        DocumentType.RI,
        "ri.pdf",
        "application/pdf",
        1234L,
        UID,
        Instant.now());
  }

  private MockMultipartFile pdf() {
    return new MockMultipartFile("file", "ri.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list()).thenReturn(List.of(view()));

    mvc.perform(get("/api/documents").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Regimento Interno"))
        .andExpect(jsonPath("$[0].type").value("RI"));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/documents")).andExpect(status().is4xxClientError());
  }

  @Test
  void upload_withDocumentManage_returns201() throws Exception {
    when(service.upload(eq(UID), eq("Regimento Interno"), eq(DocumentType.RI), any()))
        .thenReturn(view());

    mvc.perform(
            multipart("/api/documents")
                .file(pdf())
                .param("title", "Regimento Interno")
                .param("type", "RI")
                .with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("RI"));
  }

  @Test
  void upload_withoutPermission_returns403() throws Exception {
    mvc.perform(
            multipart("/api/documents")
                .file(pdf())
                .param("title", "Regimento Interno")
                .param("type", "RI")
                .with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).upload(any(), any(), any(), any());
  }

  @Test
  void upload_unauthenticated_isRejected() throws Exception {
    mvc.perform(multipart("/api/documents").file(pdf()).param("title", "x").param("type", "RI"))
        .andExpect(status().is4xxClientError());
    verify(service, never()).upload(any(), any(), any(), any());
  }

  @Test
  void upload_invalidType_mapsTo400() throws Exception {
    when(service.upload(eq(UID), any(), eq(DocumentType.OUTRO), any()))
        .thenThrow(new DocumentException("FILE_TYPE_INVALID", "Apenas PDF é aceito."));

    mvc.perform(
            multipart("/api/documents")
                .file(new MockMultipartFile("file", "x.png", "image/png", new byte[] {1}))
                .param("title", "Foto")
                .param("type", "OUTRO")
                .with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_TYPE_INVALID"));
  }

  @Test
  void download_returns200() throws Exception {
    when(service.download(DID))
        .thenReturn(
            new DocumentService.DocumentContent(new byte[] {1, 2, 3}, "application/pdf", "ri.pdf"));

    mvc.perform(get("/api/documents/{id}/file", DID).with(MockAuth.user(UID)))
        .andExpect(status().isOk());
  }

  @Test
  void download_notFound_mapsTo404() throws Exception {
    when(service.download(DID))
        .thenThrow(new DocumentException("NOT_FOUND", "Documento não encontrado."));

    mvc.perform(get("/api/documents/{id}/file", DID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void delete_withDocumentManage_returns204() throws Exception {
    mvc.perform(delete("/api/documents/{id}", DID).with(MockAuth.user(UID, MANAGE)))
        .andExpect(status().isNoContent());
    verify(service).delete(DID);
  }

  @Test
  void delete_withoutPermission_returns403() throws Exception {
    mvc.perform(delete("/api/documents/{id}", DID).with(MockAuth.user(UID)))
        .andExpect(status().isForbidden());
    verify(service, never()).delete(any());
  }
}
