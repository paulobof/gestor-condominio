package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.recommendation.dto.CreateRecommendationRequest;
import br.com.condominio.feature.recommendation.dto.RecommendationPhotoView;
import br.com.condominio.feature.recommendation.dto.RecommendationView;
import br.com.condominio.feature.recommendation.dto.UpdateRecommendationRequest;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationServiceTest {

  private RecommendationRepository repo;
  private RecommendationPhotoRepository photoRepo;
  private RecommendationOpeningHoursRepository hoursRepo;
  private TagService tagService;
  private UserRepository userRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private ApplicationEventPublisher events;
  private RecommendationService service;

  private final UUID author = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();
  private final UUID resident = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(RecommendationRepository.class);
    photoRepo = mock(RecommendationPhotoRepository.class);
    hoursRepo = mock(RecommendationOpeningHoursRepository.class);
    tagService = mock(TagService.class);
    userRepo = mock(UserRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    events = mock(ApplicationEventPublisher.class);
    service =
        new RecommendationService(
            repo, photoRepo, hoursRepo, tagService, userRepo, storage, magicBytes, props, events);
    when(repo.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
    when(photoRepo.findByRecommendationIdOrderByOrdering(any())).thenReturn(List.of());
    when(hoursRepo.findByOwnerIdOrderByDayOfWeek(any())).thenReturn(List.of());
  }

  private CreateRecommendationRequest req(boolean isResident, UUID residentId) {
    return new CreateRecommendationRequest(
        "Pintor",
        "João",
        "11999990000",
        isResident,
        residentId,
        "Rua X",
        "R$80/h",
        5,
        "ok",
        List.of(),
        List.of());
  }

  private Recommendation persisted(UUID id, UUID authorId, RecommendationStatus status) {
    Recommendation r =
        Recommendation.create(
            authorId, "Pintor", "João", "11999990000", false, null, "Rua X", "R$80/h", 5, "ok");
    ReflectionTestUtils.setField(r, "id", id);
    ReflectionTestUtils.setField(r, "status", status);
    return r;
  }

  @Test
  void create_external_active_noEvent() {
    RecommendationView v = service.create(author, req(false, null));
    assertThat(v.status()).isEqualTo(RecommendationStatus.ACTIVE);
    verify(events, never()).publishEvent(any(RecommendationConsentRequestedEvent.class));
  }

  @Test
  void create_resident_pending_publishesEvent() {
    User u = mock(User.class);
    when(u.getId()).thenReturn(resident);
    when(u.getPhone()).thenReturn("11988887777");
    when(u.getGreetingName()).thenReturn("Maria");
    when(userRepo.findById(resident)).thenReturn(Optional.of(u));
    RecommendationView v = service.create(author, req(true, resident));
    assertThat(v.status()).isEqualTo(RecommendationStatus.PENDING_RESIDENT_CONSENT);
    verify(events).publishEvent(any(RecommendationConsentRequestedEvent.class));
  }

  @Test
  void update_byStranger_forbidden() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    assertThatThrownBy(
            () ->
                service.update(
                    id,
                    stranger,
                    false,
                    new UpdateRecommendationRequest(
                        "X", null, null, null, null, null, null, List.of(), List.of())))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void update_byModerator_allowed() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    RecommendationView v =
        service.update(
            id,
            stranger,
            true,
            new UpdateRecommendationRequest(
                "Novo", null, null, null, null, null, null, List.of(), List.of()));
    assertThat(v.serviceName()).isEqualTo("Novo");
  }

  @Test
  void residentConsent_approve_activates() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    service.residentConsent(id, resident, false, true);
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void residentConsent_decline_softDeletes() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    service.residentConsent(id, resident, false, false);
    verify(repo).delete(r);
  }

  @Test
  void residentConsent_byOther_withoutModerate_forbidden() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.residentConsent(id, stranger, false, true))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void hide_byModerator() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    service.hide(id);
    verify(repo).save(any(Recommendation.class));
  }

  @Test
  void residentConsent_whenNotPending_invalidState() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.ACTIVE);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.residentConsent(id, resident, false, true))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_STATE");
  }

  @Test
  void create_resident_userNotFound_throwsNotFound() {
    when(userRepo.findById(resident)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(author, req(true, resident)))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }

  @Test
  void getById_active_visibleToAnyone() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    assertThat(service.getById(id, stranger, false).serviceName()).isEqualTo("Pintor");
  }

  @Test
  void getById_pending_byStranger_notFound() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.getById(id, stranger, false))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }

  @Test
  void getById_pending_byIndicatedResident_ok() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.PENDING_RESIDENT_CONSENT);
    ReflectionTestUtils.setField(r, "residentUserId", resident);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThat(service.getById(id, resident, false).status())
        .isEqualTo(RecommendationStatus.PENDING_RESIDENT_CONSENT);
  }

  private MockMultipartFile jpeg(int size) {
    return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[size]);
  }

  @Test
  void addPhoto_overLimit_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(photoRepo.countByRecommendationId(id)).thenReturn(5L);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_LIMIT");
  }

  @Test
  void addPhoto_invalidType_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(photoRepo.countByRecommendationId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForPhoto("application/pdf")).thenReturn(false);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TYPE_INVALID");
  }

  @Test
  void addPhoto_tooLarge_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(photoRepo.countByRecommendationId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(1_048_577)))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TOO_LARGE");
  }

  @Test
  void addPhoto_happyPath_uploadsAndSaves() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(photoRepo.countByRecommendationId(id)).thenReturn(0L);
    when(photoRepo.maxOrdering(id)).thenReturn(-1);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    when(storage.upload(eq(props.getBucketRecommendations()), any(), anyLong(), eq("image/jpeg")))
        .thenReturn("obj-key-1");
    when(photoRepo.save(any(RecommendationPhoto.class))).thenAnswer(i -> i.getArgument(0));

    RecommendationPhotoView v = service.addPhoto(id, author, false, jpeg(100));

    assertThat(v.ordering()).isEqualTo(0);
    verify(storage)
        .upload(eq(props.getBucketRecommendations()), any(), anyLong(), eq("image/jpeg"));
    verify(photoRepo).save(any(RecommendationPhoto.class));
  }

  @Test
  void removePhoto_byAuthor_softDeletes() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    RecommendationPhoto p = RecommendationPhoto.create(id, "obj-key-1", "image/jpeg", 0);
    ReflectionTestUtils.setField(p, "id", photoId);
    when(photoRepo.findByIdAndRecommendationId(photoId, id)).thenReturn(Optional.of(p));
    service.removePhoto(id, photoId, author, false);
    verify(photoRepo).delete(p);
  }

  @Test
  void removePhoto_notFound_throws() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(photoRepo.findByIdAndRecommendationId(photoId, id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.removePhoto(id, photoId, author, false))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }

  @Test
  void photoUrl_returnsPresigned() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    RecommendationPhoto p = RecommendationPhoto.create(id, "obj-key-1", "image/jpeg", 0);
    ReflectionTestUtils.setField(p, "id", photoId);
    when(photoRepo.findByIdAndRecommendationId(photoId, id)).thenReturn(Optional.of(p));
    when(storage.presignedGetUrl(eq(props.getBucketRecommendations()), eq("obj-key-1"), any()))
        .thenReturn("https://minio/obj-key-1?sig");
    assertThat(service.photoUrl(id, photoId)).startsWith("https://minio/");
  }
}
