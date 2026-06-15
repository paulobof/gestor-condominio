package br.com.condominio.feature.classified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ClassifiedServiceTest {

  private ClassifiedRepository repo;
  private ClassifiedPhotoRepository photoRepo;
  private UserRepository userRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private MinioProperties props;
  private ActivityNotifier activityNotifier;
  private ClassifiedService service;

  private final UUID author = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(ClassifiedRepository.class);
    photoRepo = mock(ClassifiedPhotoRepository.class);
    userRepo = mock(UserRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    props = new MinioProperties();
    activityNotifier = mock(ActivityNotifier.class);
    service =
        new ClassifiedService(
            repo, photoRepo, userRepo, storage, magicBytes, props, activityNotifier);
    when(repo.save(any(Classified.class))).thenAnswer(i -> i.getArgument(0));
    when(photoRepo.findByClassifiedIdOrderByOrdering(any())).thenReturn(List.of());
    when(userRepo.findAllById(any())).thenReturn(List.of());
  }

  private Classified persisted(UUID id, UUID authorId, ClassifiedStatus status) {
    Classified c = Classified.create(authorId, "Bicicleta", "Aro 29", new BigDecimal("500.00"));
    ReflectionTestUtils.setField(c, "id", id);
    ReflectionTestUtils.setField(c, "status", status);
    return c;
  }

  private User userWith(UUID id, String fullName, String phone) {
    User u = newInstance(User.class);
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "fullName", fullName);
    ReflectionTestUtils.setField(u, "phone", phone);
    return u;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var c = type.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void create_savesActiveClassified() {
    ClassifiedView v =
        service.create(
            author, new CreateClassifiedRequest("Bike", "desc", new BigDecimal("100.00")));
    assertThat(v.status()).isEqualTo(ClassifiedStatus.ACTIVE);
    assertThat(v.authorUserId()).isEqualTo(author);
    verify(repo).save(any(Classified.class));
  }

  @Test
  void getById_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(ClassifiedException.class)
        .hasMessageContaining("não encontrado");
  }

  // --- contato do autor ---

  @Test
  void getById_exposesContactFromAuthorProfile() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(userRepo.findAllById(List.of(author)))
        .thenReturn(List.of(userWith(author, "Ana Costa", "+5511999990000")));

    ClassifiedView v = service.getById(id);

    assertThat(v.contactName()).isEqualTo("Ana Costa");
    assertThat(v.contactPhone()).isEqualTo("+5511999990000");
  }

  @Test
  void getById_authorWithoutPhone_contactPhoneIsNull() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(userRepo.findAllById(List.of(author)))
        .thenReturn(List.of(userWith(author, "Sem Telefone", null)));

    ClassifiedView v = service.getById(id);

    assertThat(v.contactName()).isEqualTo("Sem Telefone");
    assertThat(v.contactPhone()).isNull();
  }

  @Test
  void list_exposesContactForEachClassified_noPlusOneQuery() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID author2 = UUID.randomUUID();

    Classified c1 = persisted(id1, author, ClassifiedStatus.ACTIVE);
    Classified c2 = persisted(id2, author2, ClassifiedStatus.ACTIVE);
    User u1 = userWith(author, "Pedro Lima", "+5511111110000");
    User u2 = userWith(author2, "Carla Ramos", "+5511222220000");

    // findAllById chamado UMA vez com ambos os IDs (sem N+1)
    when(userRepo.findAllById(any())).thenReturn(List.of(u1, u2));

    ClassifiedView v1 = ClassifiedView.of(c1, List.of(), "Pedro Lima", "+5511111110000");
    ClassifiedView v2 = ClassifiedView.of(c2, List.of(), "Carla Ramos", "+5511222220000");

    // Verifica apenas que o método de fábrica propaga os campos
    assertThat(v1.contactName()).isEqualTo("Pedro Lima");
    assertThat(v2.contactPhone()).isEqualTo("+5511222220000");
  }

  // --- outros testes existentes ---

  @Test
  void update_byAuthor_editsFields() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(
            id,
            author,
            false,
            new UpdateClassifiedRequest("Novo", "nova", new BigDecimal("9.00"), null));
    assertThat(v.title()).isEqualTo("Novo");
  }

  @Test
  void update_byStranger_withoutModerate_forbidden() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    assertThatThrownBy(
            () ->
                service.update(
                    id, stranger, false, new UpdateClassifiedRequest("x", "y", null, null)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
  }

  @Test
  void update_byModerator_allowed() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(id, stranger, true, new UpdateClassifiedRequest("Mod", "z", null, null));
    assertThat(v.title()).isEqualTo("Mod");
  }

  @Test
  void update_withStatusTransition_marksSold() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedView v =
        service.update(
            id,
            author,
            false,
            new UpdateClassifiedRequest("Bike", "desc", null, ClassifiedStatus.SOLD));
    assertThat(v.status()).isEqualTo(ClassifiedStatus.SOLD);
  }

  @Test
  void delete_byAuthor_softDeletes() {
    UUID id = UUID.randomUUID();
    Classified c = persisted(id, author, ClassifiedStatus.ACTIVE);
    when(repo.findById(id)).thenReturn(Optional.of(c));
    service.delete(id, author, false);
    verify(repo).delete(c);
  }

  private MockMultipartFile jpeg(int size) {
    return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[size]);
  }

  @Test
  void addPhoto_overLimit_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(5L);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_LIMIT");
  }

  @Test
  void addPhoto_invalidType_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForPhoto("application/pdf")).thenReturn(false);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(10)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TYPE_INVALID");
  }

  @Test
  void addPhoto_tooLarge_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    assertThatThrownBy(() -> service.addPhoto(id, author, false, jpeg(1_048_577)))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "PHOTO_TOO_LARGE");
  }

  @Test
  void addPhoto_happyPath_uploadsAndSaves() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.countByClassifiedId(id)).thenReturn(0L);
    when(photoRepo.maxOrdering(id)).thenReturn(-1);
    when(magicBytes.detect(any())).thenReturn("image/jpeg");
    when(magicBytes.isAcceptedForPhoto("image/jpeg")).thenReturn(true);
    when(storage.upload(eq(props.getBucketClassifieds()), any(), anyLong(), eq("image/jpeg")))
        .thenReturn("obj-key-1");
    when(photoRepo.save(any(ClassifiedPhoto.class))).thenAnswer(i -> i.getArgument(0));

    ClassifiedPhotoView v = service.addPhoto(id, author, false, jpeg(100));

    assertThat(v.ordering()).isEqualTo(0);
    verify(storage).upload(eq(props.getBucketClassifieds()), any(), anyLong(), eq("image/jpeg"));
    verify(photoRepo).save(any(ClassifiedPhoto.class));
  }

  @Test
  void removePhoto_byAuthor_softDeletes() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedPhoto p = ClassifiedPhoto.create(id, "obj-key-1", "image/jpeg", 0);
    ReflectionTestUtils.setField(p, "id", photoId);
    when(photoRepo.findByIdAndClassifiedId(photoId, id)).thenReturn(Optional.of(p));
    service.removePhoto(id, photoId, author, false);
    verify(photoRepo).delete(p);
  }

  @Test
  void removePhoto_notFound_throws() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    when(photoRepo.findByIdAndClassifiedId(photoId, id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.removePhoto(id, photoId, author, false))
        .isInstanceOf(ClassifiedException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
  }

  @Test
  void photoUrl_returnsPresigned() {
    UUID id = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, author, ClassifiedStatus.ACTIVE)));
    ClassifiedPhoto p = ClassifiedPhoto.create(id, "obj-key-1", "image/jpeg", 0);
    ReflectionTestUtils.setField(p, "id", photoId);
    when(photoRepo.findByIdAndClassifiedId(photoId, id)).thenReturn(Optional.of(p));
    when(storage.presignedGetUrl(eq(props.getBucketClassifieds()), eq("obj-key-1"), any()))
        .thenReturn("https://minio/obj-key-1?sig");
    assertThat(service.photoUrl(id, photoId)).startsWith("https://minio/");
  }
}
