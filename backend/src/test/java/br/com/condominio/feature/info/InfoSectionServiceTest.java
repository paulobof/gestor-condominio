package br.com.condominio.feature.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
import br.com.condominio.shared.html.HtmlSanitizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfoSectionServiceTest {

  @Mock private InfoSectionRepository repo;
  @Mock private HtmlSanitizer sanitizer;
  @Mock private ActivityNotifier activityNotifier;
  @InjectMocks private InfoSectionService service;

  @BeforeEach
  void echoSanitizer() {
    lenient().when(sanitizer.sanitize(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void create_calculatesNextPosition_andSanitizesBody() {
    when(repo.findMaxPosition()).thenReturn(2);
    when(sanitizer.sanitize("<p>x</p><script>bad</script>")).thenReturn("<p>x</p>");
    when(repo.save(any(InfoSection.class))).thenAnswer(i -> i.getArgument(0));

    var view =
        service.create(new CreateInfoSectionRequest("Portaria", "<p>x</p><script>bad</script>"));

    assertThat(view.position()).isEqualTo(3);
    assertThat(view.body()).isEqualTo("<p>x</p>");
    assertThat(view.title()).isEqualTo("Portaria");
  }

  @Test
  void create_firstSection_positionZero() {
    when(repo.findMaxPosition()).thenReturn(null);
    when(repo.save(any(InfoSection.class))).thenAnswer(i -> i.getArgument(0));

    var view = service.create(new CreateInfoSectionRequest("Regras", "<p>r</p>"));

    assertThat(view.position()).isZero();
  }

  @Test
  void update_editsAndSanitizes() {
    UUID id = UUID.randomUUID();
    InfoSection existing = InfoSection.create("Old", "<p>old</p>", 0);
    when(repo.findById(id)).thenReturn(Optional.of(existing));
    when(sanitizer.sanitize("<p>new</p>")).thenReturn("<p>new</p>");

    var view = service.update(id, new UpdateInfoSectionRequest("New", "<p>new</p>"));

    assertThat(view.title()).isEqualTo("New");
    assertThat(view.body()).isEqualTo("<p>new</p>");
  }

  @Test
  void update_notFound_throws() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(id, new UpdateInfoSectionRequest("a", "<p>b</p>")))
        .isInstanceOf(InfoException.class)
        .hasMessageContaining("não encontrada");
  }

  @Test
  void list_returnsOrderedByPosition() {
    when(repo.findAllByOrderByPositionAsc())
        .thenReturn(
            List.of(
                InfoSection.create("A", "<p>a</p>", 0), InfoSection.create("B", "<p>b</p>", 1)));

    var views = service.list();

    assertThat(views).extracting(v -> v.title()).containsExactly("A", "B");
  }

  @Test
  void reorder_appliesPositions() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    InfoSection s1 = InfoSection.create("A", "<p>a</p>", 0);
    InfoSection s2 = InfoSection.create("B", "<p>b</p>", 1);
    when(repo.findById(id1)).thenReturn(Optional.of(s1));
    when(repo.findById(id2)).thenReturn(Optional.of(s2));

    service.reorder(
        List.of(new ReorderInfoRequest.Item(id1, 1), new ReorderInfoRequest.Item(id2, 0)));

    assertThat(s1.getPosition()).isEqualTo(1);
    assertThat(s2.getPosition()).isEqualTo(0);
  }
}
