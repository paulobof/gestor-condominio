package br.com.condominio.feature.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.announcement.dto.AnnouncementView;
import br.com.condominio.feature.announcement.dto.CreateAnnouncementRequest;
import br.com.condominio.feature.announcement.dto.ReorderAnnouncementsRequest;
import br.com.condominio.feature.announcement.dto.UpdateAnnouncementRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class AnnouncementServiceTest {

  private AnnouncementRepository repo;
  private AnnouncementService service;

  private final UUID author = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(AnnouncementRepository.class);
    service = new AnnouncementService(repo);
    when(repo.save(any(Announcement.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Announcement persisted(UUID id, int position) {
    Announcement a = Announcement.create(author, "Aviso", "corpo", position);
    ReflectionTestUtils.setField(a, "id", id);
    return a;
  }

  @Test
  void create_putsNewAtTop_minusOne() {
    when(repo.findMinPosition()).thenReturn(2);

    AnnouncementView v =
        service.create(author, new CreateAnnouncementRequest("Manutenção", "corpo"));

    assertThat(v.title()).isEqualTo("Manutenção");
    assertThat(v.authorUserId()).isEqualTo(author);
    assertThat(v.position()).isEqualTo(1);
    verify(repo).save(any(Announcement.class));
  }

  @Test
  void create_firstAnnouncement_positionZero() {
    when(repo.findMinPosition()).thenReturn(null);

    AnnouncementView v = service.create(author, new CreateAnnouncementRequest("Regras", "corpo"));

    assertThat(v.position()).isZero();
  }

  @Test
  void getById_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(AnnouncementException.class)
        .hasMessageContaining("não encontrado");
  }

  @Test
  void update_editsFields() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, 0)));

    AnnouncementView v = service.update(id, new UpdateAnnouncementRequest("Novo", "novo corpo"));

    assertThat(v.title()).isEqualTo("Novo");
    assertThat(v.body()).isEqualTo("novo corpo");
    assertThat(v.position()).isZero();
  }

  @Test
  void delete_softDeletesViaRepository() {
    UUID id = UUID.randomUUID();
    Announcement a = persisted(id, 0);
    when(repo.findById(id)).thenReturn(Optional.of(a));

    service.delete(id);

    verify(repo).delete(a);
  }

  @Test
  void reorder_appliesPositions() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Announcement a1 = persisted(id1, 0);
    Announcement a2 = persisted(id2, 1);
    when(repo.findById(id1)).thenReturn(Optional.of(a1));
    when(repo.findById(id2)).thenReturn(Optional.of(a2));

    service.reorder(
        List.of(
            new ReorderAnnouncementsRequest.Item(id1, 1),
            new ReorderAnnouncementsRequest.Item(id2, 0)));

    assertThat(a1.getPosition()).isEqualTo(1);
    assertThat(a2.getPosition()).isZero();
  }

  @Test
  void list_mapsPageToViews() {
    when(repo.findAllByOrderByPositionAsc(any()))
        .thenReturn(new PageImpl<>(List.of(persisted(UUID.randomUUID(), 0))));

    var page = service.list(PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).position()).isZero();
  }
}
