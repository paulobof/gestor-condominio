package br.com.condominio.feature.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.announcement.dto.AnnouncementView;
import br.com.condominio.feature.announcement.dto.CreateAnnouncementRequest;
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

  private Announcement persisted(UUID id, boolean pinned) {
    Announcement a = Announcement.create(author, "Aviso", "corpo", pinned);
    ReflectionTestUtils.setField(a, "id", id);
    return a;
  }

  @Test
  void create_savesAndReturnsView() {
    AnnouncementView v =
        service.create(author, new CreateAnnouncementRequest("Manutenção", "corpo", true));

    assertThat(v.title()).isEqualTo("Manutenção");
    assertThat(v.pinned()).isTrue();
    assertThat(v.authorUserId()).isEqualTo(author);
    verify(repo).save(any(Announcement.class));
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
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, true)));

    AnnouncementView v =
        service.update(id, new UpdateAnnouncementRequest("Novo", "novo corpo", false));

    assertThat(v.title()).isEqualTo("Novo");
    assertThat(v.pinned()).isFalse();
  }

  @Test
  void delete_softDeletesViaRepository() {
    UUID id = UUID.randomUUID();
    Announcement a = persisted(id, false);
    when(repo.findById(id)).thenReturn(Optional.of(a));

    service.delete(id);

    verify(repo).delete(a);
  }

  @Test
  void list_mapsPageToViews() {
    when(repo.findAllByOrderByPinnedDescPublishedAtDesc(any()))
        .thenReturn(new PageImpl<>(List.of(persisted(UUID.randomUUID(), true))));

    var page = service.list(PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).pinned()).isTrue();
  }
}
