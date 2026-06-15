package br.com.condominio.feature.faq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.faq.dto.CreateFaqRequest;
import br.com.condominio.feature.faq.dto.FaqView;
import br.com.condominio.feature.faq.dto.ReorderFaqRequest;
import br.com.condominio.feature.faq.dto.UpdateFaqRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FaqServiceTest {

  private FaqRepository repo;
  private ActivityNotifier activityNotifier;
  private FaqService service;

  @BeforeEach
  void setUp() {
    repo = mock(FaqRepository.class);
    activityNotifier = mock(ActivityNotifier.class);
    service = new FaqService(repo, activityNotifier);
    when(repo.save(any(Faq.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Faq persisted(
      UUID id, String question, String category, boolean published, int ordering) {
    Faq f = Faq.create(question, "resposta", category, published, ordering);
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  // --- listPublished ---

  @Test
  void listPublished_mapsPublishedFaqsToViews() {
    UUID id = UUID.randomUUID();
    Faq faq = persisted(id, "Como reservar?", "Reservas", true, 1);
    when(repo.findAllByPublishedTrueOrderByCategoryAscOrderingAsc()).thenReturn(List.of(faq));

    List<FaqView> views = service.listPublished();

    assertThat(views).hasSize(1);
    assertThat(views.get(0).question()).isEqualTo("Como reservar?");
    assertThat(views.get(0).published()).isTrue();
    assertThat(views.get(0).id()).isEqualTo(id);
  }

  @Test
  void listPublished_returnsEmptyWhenNoPublishedFaqs() {
    when(repo.findAllByPublishedTrueOrderByCategoryAscOrderingAsc()).thenReturn(List.of());

    List<FaqView> views = service.listPublished();

    assertThat(views).isEmpty();
  }

  // --- listAll ---

  @Test
  void listAll_mapsAllFaqsToViews() {
    when(repo.findAllByOrderByCategoryAscOrderingAsc())
        .thenReturn(
            List.of(
                persisted(UUID.randomUUID(), "P1", "Cat", true, 0),
                persisted(UUID.randomUUID(), "P2", "Cat", false, 1)));

    List<FaqView> views = service.listAll();

    assertThat(views).hasSize(2);
  }

  // --- getById ---

  @Test
  void getById_returnsViewForExistingFaq() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id))
        .thenReturn(Optional.of(persisted(id, "Qual o horário?", "Geral", true, 0)));

    FaqView view = service.getById(id);

    assertThat(view.question()).isEqualTo("Qual o horário?");
    assertThat(view.id()).isEqualTo(id);
  }

  @Test
  void getById_notFound_throwsFaqExceptionWithNotFoundCode() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(id))
        .isInstanceOf(FaqException.class)
        .satisfies(ex -> assertThat(((FaqException) ex).getCode()).isEqualTo("NOT_FOUND"))
        .hasMessageContaining("não encontrado");
  }

  // --- create ---

  @Test
  void create_savesAndReturnsView() {
    when(repo.findMaxOrderingByCategory("Financeiro")).thenReturn(null);
    CreateFaqRequest req = new CreateFaqRequest("Como pagar?", "Via app.", "Financeiro", true);

    FaqView view = service.create(req);

    assertThat(view.question()).isEqualTo("Como pagar?");
    assertThat(view.answer()).isEqualTo("Via app.");
    assertThat(view.category()).isEqualTo("Financeiro");
    assertThat(view.published()).isTrue();
    assertThat(view.ordering()).isEqualTo(0);
    verify(repo).save(any(Faq.class));
  }

  @Test
  void create_unpublished_savedWithPublishedFalse() {
    when(repo.findMaxOrderingByCategory("Geral")).thenReturn(null);
    CreateFaqRequest req = new CreateFaqRequest("Rascunho?", "Em construção.", "Geral", false);

    FaqView view = service.create(req);

    assertThat(view.published()).isFalse();
    verify(repo).save(any(Faq.class));
  }

  @Test
  void create_assignsMaxPlusOneOrdering_whenCategoryHasExistingFaqs() {
    when(repo.findMaxOrderingByCategory("Regras")).thenReturn(2);
    CreateFaqRequest req = new CreateFaqRequest("Nova regra?", "Sim.", "Regras", true);

    FaqView view = service.create(req);

    assertThat(view.ordering()).isEqualTo(3);
    verify(repo).findMaxOrderingByCategory("Regras");
  }

  @Test
  void create_assignsZeroOrdering_whenCategoryIsEmpty() {
    when(repo.findMaxOrderingByCategory("Nova Cat")).thenReturn(null);
    CreateFaqRequest req = new CreateFaqRequest("Primeira?", "Resposta.", "Nova Cat", false);

    FaqView view = service.create(req);

    assertThat(view.ordering()).isEqualTo(0);
    verify(repo).findMaxOrderingByCategory("Nova Cat");
  }

  // --- update ---

  @Test
  void update_editsQuestionAnswerCategoryAndPublished() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, "Antiga", "Velha", false, 2)));

    FaqView view =
        service.update(id, new UpdateFaqRequest("Nova", "Novo corpo.", "Nova Cat", true));

    assertThat(view.question()).isEqualTo("Nova");
    assertThat(view.answer()).isEqualTo("Novo corpo.");
    assertThat(view.category()).isEqualTo("Nova Cat");
    assertThat(view.published()).isTrue();
  }

  @Test
  void update_notFound_throwsFaqException() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(id, new UpdateFaqRequest("Q", "A", "C", true)))
        .isInstanceOf(FaqException.class)
        .satisfies(ex -> assertThat(((FaqException) ex).getCode()).isEqualTo("NOT_FOUND"));
  }

  // --- setPublished ---

  @Test
  void setPublished_true_setsPublishedFlagOnFaq() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, "Q", "C", false, 0)));

    FaqView view = service.setPublished(id, true);

    assertThat(view.published()).isTrue();
  }

  @Test
  void setPublished_false_unpublishesFaq() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.of(persisted(id, "Q", "C", true, 0)));

    FaqView view = service.setPublished(id, false);

    assertThat(view.published()).isFalse();
  }

  @Test
  void setPublished_notFound_throwsFaqException() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setPublished(id, true))
        .isInstanceOf(FaqException.class)
        .satisfies(ex -> assertThat(((FaqException) ex).getCode()).isEqualTo("NOT_FOUND"));
  }

  // --- reorder ---

  @Test
  void reorder_setsOrderingOnEachFaq() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Faq f1 = persisted(id1, "Q1", "Cat", true, 0);
    Faq f2 = persisted(id2, "Q2", "Cat", true, 1);
    when(repo.findById(id1)).thenReturn(Optional.of(f1));
    when(repo.findById(id2)).thenReturn(Optional.of(f2));

    service.reorder(
        List.of(new ReorderFaqRequest.Item(id1, 5), new ReorderFaqRequest.Item(id2, 10)));

    assertThat(f1.getOrdering()).isEqualTo(5);
    assertThat(f2.getOrdering()).isEqualTo(10);
  }

  @Test
  void reorder_itemNotFound_throwsFaqException() {
    UUID missingId = UUID.randomUUID();
    when(repo.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reorder(List.of(new ReorderFaqRequest.Item(missingId, 1))))
        .isInstanceOf(FaqException.class)
        .satisfies(ex -> assertThat(((FaqException) ex).getCode()).isEqualTo("NOT_FOUND"));
  }

  // --- delete ---

  @Test
  void delete_callsRepositoryDeleteWithFaq() {
    UUID id = UUID.randomUUID();
    Faq faq = persisted(id, "Q", "C", true, 0);
    when(repo.findById(id)).thenReturn(Optional.of(faq));

    service.delete(id);

    verify(repo).delete(faq);
  }

  @Test
  void delete_notFound_throwsFaqException() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(FaqException.class)
        .satisfies(ex -> assertThat(((FaqException) ex).getCode()).isEqualTo("NOT_FOUND"));
  }
}
