package br.com.condominio.feature.document;

import br.com.condominio.feature.document.dto.DocumentView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos do condomínio. Leitura/download liberados a qualquer autenticado; upload/exclusão só
 * com a authority {@code DOCUMENT_MANAGE} (role "Editor de Documentos"). Gated pela flag {@code
 * app.feature.documents.enabled}.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.documents.enabled", havingValue = "true")
public class DocumentController {

  private final DocumentService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<DocumentView> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
  public ResponseEntity<DocumentView> upload(
      @RequestParam("title") String title,
      @RequestParam("type") DocumentType type,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.upload(me.userId(), title, type, file));
  }

  @GetMapping("/{id}/file")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<byte[]> download(@PathVariable UUID id) {
    DocumentService.DocumentContent c = service.download(id);
    MediaType contentType =
        c.contentType() != null
            ? MediaType.parseMediaType(c.contentType())
            : MediaType.APPLICATION_OCTET_STREAM;
    String filename = c.filename() != null ? c.filename() : "documento.pdf";
    return ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .header("Referrer-Policy", "no-referrer")
        .body(c.content());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
