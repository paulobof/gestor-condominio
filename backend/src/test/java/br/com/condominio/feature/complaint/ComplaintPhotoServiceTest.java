package br.com.condominio.feature.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintPhotoServiceTest {

  private ComplaintRepository repo;
  private ComplaintPhotoRepository photoRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private ComplaintEventWriter events;
  private ComplaintPhotoService service;

  private final UUID author = UUID.randomUUID();
  private Complaint complaint;

  @BeforeEach
  void setUp() {
    repo = mock(ComplaintRepository.class);
    photoRepo = mock(ComplaintPhotoRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    events = mock(ComplaintEventWriter.class);
    service =
        new ComplaintPhotoService(
            repo, photoRepo, storage, magicBytes, new MinioProperties(), events);

    complaint =
        Complaint.create(
            author,
            UUID.randomUUID(),
            new ComplaintFields(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Lâmpada queimada na G1"));
    ReflectionTestUtils.setField(complaint, "id", UUID.randomUUID());
    when(repo.findById(complaint.getId())).thenReturn(Optional.of(complaint));
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    when(storage.upload(eq("complaints"), any(), anyLong(), eq("image/jpeg"))).thenReturn("k1");
    when(photoRepo.save(any()))
        .thenAnswer(
            i -> {
              ComplaintPhoto p = i.getArgument(0);
              ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
              return p;
            });
  }

  private MockMultipartFile file(int bytes) {
    return new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[bytes]);
  }

  @Test
  void addPhoto_uploadsToComplaintsBucketAndRecordsEvent() {
    var view = service.addPhoto(complaint.getId(), author, file(1000));
    assertThat(view.contentType()).isEqualTo("image/jpeg");
    verify(storage).upload(eq("complaints"), any(), eq(1000L), eq("image/jpeg"));
    verify(events)
        .record(eq(complaint.getId()), argThat(ch -> ch.kind() == ComplaintEventKind.PHOTO_ADDED));
  }

  @Test
  void addPhoto_fourth_isRejected() {
    when(photoRepo.countByComplaintId(complaint.getId())).thenReturn(3L);
    assertThatThrownBy(() -> service.addPhoto(complaint.getId(), author, file(10)))
        .extracting("code")
        .isEqualTo("PHOTO_LIMIT");
    verifyNoInteractions(storage);
  }

  @Test
  void addPhoto_overOneMegabyte_isRejected() {
    assertThatThrownBy(() -> service.addPhoto(complaint.getId(), author, file(1_048_577)))
        .extracting("code")
        .isEqualTo("PHOTO_TOO_LARGE");
    verifyNoInteractions(storage);
  }

  @Test
  void addPhoto_notAnImage_isRejected() {
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForPhoto("application/pdf")).thenReturn(false);
    assertThatThrownBy(() -> service.addPhoto(complaint.getId(), author, file(10)))
        .extracting("code")
        .isEqualTo("PHOTO_TYPE_INVALID");
  }

  @Test
  void addPhoto_byStranger_isNotFound() {
    assertThatThrownBy(() -> service.addPhoto(complaint.getId(), UUID.randomUUID(), file(10)))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void addPhoto_afterReviewStarted_isInvalidState() {
    ReflectionTestUtils.setField(complaint, "status", ComplaintStatus.IN_REVIEW);
    assertThatThrownBy(() -> service.addPhoto(complaint.getId(), author, file(10)))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void photoUrl_strangerNotOnBoard_isNotFound() {
    UUID photoId = UUID.randomUUID();
    assertThatThrownBy(() -> service.photoUrl(complaint.getId(), photoId, UUID.randomUUID(), false))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
    verifyNoInteractions(storage);
  }

  @Test
  void photoUrl_manager_getsPresignedUrl() {
    UUID photoId = UUID.randomUUID();
    ComplaintPhoto p = ComplaintPhoto.create(complaint.getId(), "k1", "image/jpeg", 10, 0);
    when(photoRepo.findByIdAndComplaintId(photoId, complaint.getId())).thenReturn(Optional.of(p));
    when(storage.presignedGetUrl(eq("complaints"), eq("k1"), any())).thenReturn("http://u");

    assertThat(service.photoUrl(complaint.getId(), photoId, UUID.randomUUID(), true))
        .isEqualTo("http://u");
  }
}
