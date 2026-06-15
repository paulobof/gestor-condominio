package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.recommendation.dto.CommentView;
import br.com.condominio.feature.recommendation.dto.CreateRecommendationRequest;
import br.com.condominio.feature.recommendation.dto.RecommendationPhotoView;
import br.com.condominio.feature.recommendation.dto.RecommendationView;
import br.com.condominio.feature.recommendation.dto.UpdateRecommendationRequest;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationServiceTest {

  private RecommendationRepository repo;
  private RecommendationPhotoRepository photoRepo;
  private RecommendationOpeningHoursRepository hoursRepo;
  private TagService tagService;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private UserRepository userRepo;
  private UnitRepository unitRepo;
  private RecommendationVoteRepository voteRepo;
  private RecommendationCommentRepository commentRepo;
  private ActivityNotifier activityNotifier;
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
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    userRepo = mock(UserRepository.class);
    unitRepo = mock(UnitRepository.class);
    voteRepo = mock(RecommendationVoteRepository.class);
    commentRepo = mock(RecommendationCommentRepository.class);
    activityNotifier = mock(ActivityNotifier.class);
    service =
        new RecommendationService(
            repo,
            photoRepo,
            hoursRepo,
            tagService,
            storage,
            magicBytes,
            props,
            userRepo,
            unitRepo,
            voteRepo,
            commentRepo,
            activityNotifier);
    when(repo.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
    when(photoRepo.findByRecommendationIdOrderByOrdering(any())).thenReturn(List.of());
    when(hoursRepo.findByOwnerIdOrderByDayOfWeek(any())).thenReturn(List.of());
    when(voteRepo.findByRecommendationIdAndUserId(any(), any())).thenReturn(Optional.empty());
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
        List.of(),
        null,
        null,
        null,
        null);
  }

  private Recommendation persisted(UUID id, UUID authorId, RecommendationStatus status) {
    Recommendation r =
        Recommendation.create(
            authorId,
            "Pintor",
            "João",
            "11999990000",
            false,
            null,
            "Rua X",
            "R$80/h",
            5,
            "ok",
            null,
            null,
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(r, "id", id);
    ReflectionTestUtils.setField(r, "status", status);
    return r;
  }

  @Test
  void create_external_active() {
    RecommendationView v = service.create(author, null, false, req(false, null));
    assertThat(v.status()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void create_resident_active_noApproval() {
    // Indicação de morador não exige mais consentimento: entra ACTIVE direto, sem evento.
    // Admin indica em nome de outro morador (residentUserId != null).
    UUID residentUnitId = UUID.randomUUID();
    when(userRepo.findUnitIdById(resident)).thenReturn(Optional.of(residentUnitId));
    when(unitRepo.findCodeById(residentUnitId)).thenReturn(Optional.of("T1-A-101"));
    RecommendationView v = service.create(author, null, false, req(true, resident));
    assertThat(v.status()).isEqualTo(RecommendationStatus.ACTIVE);
    assertThat(v.isResident()).isTrue();
  }

  @Test
  void create_residentSouEu_resolvesAuthorUnit() {
    // "sou eu" path: morador marca a si próprio
    UUID unitId = UUID.randomUUID();
    when(unitRepo.findCodeById(unitId)).thenReturn(Optional.of("T1-A-101"));
    CreateRecommendationRequest req = req(true, null); // residentUserId=null
    RecommendationView v = service.create(author, unitId, true, req);
    assertThat(v.isResident()).isTrue();
    assertThat(v.ownerUnitId()).isEqualTo(unitId);
    assertThat(v.ownerUnitCode()).isEqualTo("T1-A-101");
  }

  @Test
  void create_residentSouEu_noUnit_throws() {
    // "sou eu" mas autor não tem unidade
    CreateRecommendationRequest req = req(true, null);
    assertThatThrownBy(() -> service.create(author, null, false, req))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "OWNER_UNIT_REQUIRED");
  }

  @Test
  void create_residentOtherUser_resolvesTheirUnit() {
    UUID residentId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepo.findUnitIdById(residentId)).thenReturn(Optional.of(unitId));
    when(unitRepo.findCodeById(unitId)).thenReturn(Optional.of("T1-B-202"));
    CreateRecommendationRequest req = req(true, residentId);
    RecommendationView v = service.create(author, null, false, req);
    assertThat(v.ownerUnitId()).isEqualTo(unitId);
    assertThat(v.ownerUnitCode()).isEqualTo("T1-B-202");
  }

  @Test
  void create_residentOtherUser_notFound_throws() {
    UUID residentId = UUID.randomUUID();
    when(userRepo.findUnitIdById(residentId)).thenReturn(Optional.empty());
    CreateRecommendationRequest req = req(true, residentId);
    assertThatThrownBy(() -> service.create(author, null, false, req))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "RESIDENT_NOT_FOUND");
  }

  @Test
  void create_external_noOwnerUnit() {
    CreateRecommendationRequest req = req(false, null);
    RecommendationView v = service.create(author, UUID.randomUUID(), false, req);
    assertThat(v.ownerUnitId()).isNull();
    assertThat(v.ownerUnitCode()).isNull();
  }

  @Test
  void create_withInstagramUrl_stored() {
    CreateRecommendationRequest req =
        new CreateRecommendationRequest(
            "Pintor",
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            "https://instagram.com/joao",
            null,
            null,
            null);
    RecommendationView v = service.create(author, null, false, req);
    assertThat(v.instagramUrl()).isEqualTo("https://instagram.com/joao");
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
                        "X", null, null, null, null, null, null, List.of(), List.of(), null, null,
                        null, null)))
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
                "Novo", null, null, null, null, null, null, List.of(), List.of(), null, null, null,
                null));
    assertThat(v.serviceName()).isEqualTo("Novo");
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
  void getById_active_visibleToAnyone() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    assertThat(service.getById(id, stranger, false).serviceName()).isEqualTo("Pintor");
  }

  @Test
  void getById_hidden_byStranger_notFound() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.HIDDEN);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThatThrownBy(() -> service.getById(id, stranger, false))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }

  @Test
  void getById_hidden_byModerator_ok() {
    UUID id = UUID.randomUUID();
    Recommendation r = persisted(id, author, RecommendationStatus.HIDDEN);
    when(repo.findById(id)).thenReturn(Optional.of(r));
    assertThat(service.getById(id, stranger, true).status()).isEqualTo(RecommendationStatus.HIDDEN);
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

  // ---- votos e comentários --------------------------------------------------------

  @Test
  void vote_firstLike_savesAndRecomputesCount() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(voteRepo.countByRecommendationIdAndValue(id, VoteValue.LIKE)).thenReturn(1L);
    when(voteRepo.countByRecommendationIdAndValue(id, VoteValue.DISLIKE)).thenReturn(0L);

    RecommendationView v = service.vote(id, stranger, VoteValue.LIKE);

    verify(voteRepo).save(any(RecommendationVote.class));
    assertThat(v.likeCount()).isEqualTo(1);
  }

  @Test
  void vote_sameValue_togglesOff() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    RecommendationVote existing = RecommendationVote.create(id, stranger, VoteValue.LIKE);
    when(voteRepo.findByRecommendationIdAndUserId(id, stranger)).thenReturn(Optional.of(existing));

    service.vote(id, stranger, VoteValue.LIKE);

    verify(voteRepo).delete(existing);
  }

  @Test
  void vote_differentValue_switches() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    RecommendationVote existing = RecommendationVote.create(id, stranger, VoteValue.LIKE);
    when(voteRepo.findByRecommendationIdAndUserId(id, stranger)).thenReturn(Optional.of(existing));

    service.vote(id, stranger, VoteValue.DISLIKE);

    assertThat(existing.getValue()).isEqualTo(VoteValue.DISLIKE);
    verify(voteRepo).save(existing);
  }

  @Test
  void addComment_stripsAndSaves() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, author, RecommendationStatus.ACTIVE)));
    when(commentRepo.save(any(RecommendationComment.class))).thenAnswer(i -> i.getArgument(0));
    when(userRepo.findAllById(any())).thenReturn(List.of());

    CommentView c = service.addComment(id, stranger, "  Muito bom!  ");

    assertThat(c.text()).isEqualTo("Muito bom!");
    verify(commentRepo).save(any(RecommendationComment.class));
  }

  @Test
  void deleteComment_byStranger_forbidden() {
    UUID id = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    RecommendationComment c = RecommendationComment.create(id, author, "x");
    when(commentRepo.findByIdAndRecommendationId(commentId, id)).thenReturn(Optional.of(c));

    assertThatThrownBy(() -> service.deleteComment(id, commentId, stranger, false))
        .isInstanceOf(RecommendationException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void deleteComment_byAuthor_deletes() {
    UUID id = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    RecommendationComment c = RecommendationComment.create(id, author, "x");
    when(commentRepo.findByIdAndRecommendationId(commentId, id)).thenReturn(Optional.of(c));

    service.deleteComment(id, commentId, author, false);

    verify(commentRepo).delete(c);
  }
}
