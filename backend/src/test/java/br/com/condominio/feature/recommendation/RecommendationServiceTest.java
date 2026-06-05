package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.recommendation.dto.CreateRecommendationRequest;
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
}
