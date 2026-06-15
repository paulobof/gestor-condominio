package br.com.condominio.feature.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.document.dto.DocumentView;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentServiceTest {

  private static final UUID UPLOADER = UUID.randomUUID();

  private DocumentRepository repo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private ActivityNotifier activityNotifier;
  private DocumentService service;

  @BeforeEach
  void setUp() {
    repo = mock(DocumentRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    activityNotifier = mock(ActivityNotifier.class);
    service = new DocumentService(repo, storage, magicBytes, props, activityNotifier);
    when(repo.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));
  }

  private MockMultipartFile pdf() {
    return new MockMultipartFile("file", "ri.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  @Test
  void upload_pdf_happyPath_uploadsToDocumentsBucketAndSaves() {
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForDocument("application/pdf")).thenReturn(true);
    when(storage.upload(eq(props.getBucketDocuments()), any(), anyLong(), eq("application/pdf")))
        .thenReturn("obj-key-1");

    DocumentView v = service.upload(UPLOADER, "Regimento Interno", DocumentType.RI, pdf());

    assertThat(v.title()).isEqualTo("Regimento Interno");
    assertThat(v.type()).isEqualTo(DocumentType.RI);
    assertThat(v.uploadedByUserId()).isEqualTo(UPLOADER);
    verify(storage).upload(eq(props.getBucketDocuments()), any(), anyLong(), eq("application/pdf"));
  }

  @Test
  void upload_nonPdf_rejected() {
    when(magicBytes.detect(any())).thenReturn("image/png");
    when(magicBytes.isAcceptedForDocument("image/png")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.upload(
                    UPLOADER,
                    "Foto",
                    DocumentType.OUTRO,
                    new MockMultipartFile("file", "x.png", "image/png", new byte[] {1})))
        .isInstanceOf(DocumentException.class)
        .hasFieldOrPropertyWithValue("code", "FILE_TYPE_INVALID");
    verify(storage, never()).upload(any(), any(), anyLong(), any());
  }

  @Test
  void upload_tooLarge_rejectedBeforeReading() {
    MultipartFile big = mock(MultipartFile.class);
    when(big.isEmpty()).thenReturn(false);
    when(big.getSize()).thenReturn(DocumentService.MAX_DOCUMENT_BYTES + 1);

    assertThatThrownBy(() -> service.upload(UPLOADER, "Grande", DocumentType.AGE, big))
        .isInstanceOf(DocumentException.class)
        .hasFieldOrPropertyWithValue("code", "FILE_TOO_LARGE");
    verify(storage, never()).upload(any(), any(), anyLong(), any());
  }

  @Test
  void upload_blankTitle_rejected() {
    assertThatThrownBy(() -> service.upload(UPLOADER, "   ", DocumentType.RI, pdf()))
        .isInstanceOf(DocumentException.class)
        .hasFieldOrPropertyWithValue("code", "TITLE_REQUIRED");
  }

  @Test
  void list_returnsViews() {
    Document d =
        Document.create("RI 2026", DocumentType.RI, "k", "ri.pdf", "application/pdf", 10, UPLOADER);
    when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d));

    List<DocumentView> out = service.list();

    assertThat(out).hasSize(1);
    assertThat(out.get(0).title()).isEqualTo("RI 2026");
  }

  @Test
  void download_returnsContent() {
    UUID id = UUID.randomUUID();
    Document d =
        Document.create("RI", DocumentType.RI, "obj-key", "ri.pdf", "application/pdf", 3, UPLOADER);
    when(repo.findById(id)).thenReturn(Optional.of(d));
    when(storage.getObject(props.getBucketDocuments(), "obj-key")).thenReturn(new byte[] {9, 9});

    DocumentService.DocumentContent c = service.download(id);

    assertThat(c.content()).containsExactly(9, 9);
    assertThat(c.contentType()).isEqualTo("application/pdf");
    assertThat(c.filename()).isEqualTo("ri.pdf");
  }

  @Test
  void delete_softDeletesAndRemovesObject() {
    UUID id = UUID.randomUUID();
    Document d =
        Document.create("RI", DocumentType.RI, "obj-key", "ri.pdf", "application/pdf", 3, UPLOADER);
    when(repo.findById(id)).thenReturn(Optional.of(d));

    service.delete(id);

    verify(repo).delete(d);
    verify(storage).delete(props.getBucketDocuments(), "obj-key");
  }

  @Test
  void download_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.download(id))
        .isInstanceOf(DocumentException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }
}
