package br.com.condominio.feature.document;

import br.com.condominio.feature.document.dto.DocumentView;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

  static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024; // 5MB (limite global de multipart)

  private final DocumentRepository repo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;

  /** NÃO transacional: upload pro MinIO acontece fora de transação (CLAUDE.md). */
  public DocumentView upload(UUID uploaderId, String title, DocumentType type, MultipartFile file) {
    String t = title == null ? null : title.strip();
    if (t == null || t.isEmpty()) {
      throw new DocumentException("TITLE_REQUIRED", "Título é obrigatório.");
    }
    if (t.length() > 180) {
      throw new DocumentException("TITLE_TOO_LONG", "Título deve ter no máximo 180 caracteres.");
    }
    if (type == null) {
      throw new DocumentException("TYPE_REQUIRED", "Tipo do documento é obrigatório.");
    }
    if (file == null || file.isEmpty()) {
      throw new DocumentException("FILE_REQUIRED", "Arquivo é obrigatório.");
    }
    if (file.getSize() > MAX_DOCUMENT_BYTES) {
      throw new DocumentException("FILE_TOO_LARGE", "Documento deve ter no máximo 5MB.");
    }

    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = magicBytes.detect(in);
    } catch (IOException e) {
      throw new DocumentException("FILE_READ_FAILED", "Falha ao ler o arquivo.");
    }
    if (!magicBytes.isAcceptedForDocument(mime)) {
      throw new DocumentException("FILE_TYPE_INVALID", "Apenas PDF é aceito.");
    }

    String objectKey;
    try (InputStream in = file.getInputStream()) {
      objectKey = storage.upload(props.getBucketDocuments(), in, file.getSize(), mime);
    } catch (IOException e) {
      throw new DocumentException("FILE_UPLOAD_FAILED", "Falha ao enviar o arquivo.");
    }

    Document saved =
        repo.save(
            Document.create(
                t, type, objectKey, file.getOriginalFilename(), mime, file.getSize(), uploaderId));
    log.info("document.uploaded id={} type={} by={}", saved.getId(), type, uploaderId);
    return DocumentView.of(saved);
  }

  @Transactional(readOnly = true)
  public List<DocumentView> list() {
    return repo.findAllByOrderByCreatedAtDesc().stream().map(DocumentView::of).toList();
  }

  @Transactional(readOnly = true)
  public DocumentContent download(UUID id) {
    Document d = load(id);
    byte[] content = storage.getObject(props.getBucketDocuments(), d.getObjectKey());
    return new DocumentContent(content, d.getContentType(), d.getFilename());
  }

  @Transactional
  public void delete(UUID id) {
    Document d = load(id);
    String objectKey = d.getObjectKey();
    repo.delete(d);
    try {
      storage.delete(props.getBucketDocuments(), objectKey);
    } catch (Exception e) {
      log.warn("document.delete: falha removendo objeto {}: {}", id, e.getMessage());
    }
    log.info("document.deleted id={}", id);
  }

  private Document load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new DocumentException("NOT_FOUND", "Documento não encontrado."));
  }

  public record DocumentContent(byte[] content, String contentType, String filename) {}
}
