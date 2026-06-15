package br.com.condominio.feature.faq;

import br.com.condominio.feature.activity.ActivityAction;
import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.faq.dto.CreateFaqRequest;
import br.com.condominio.feature.faq.dto.FaqView;
import br.com.condominio.feature.faq.dto.ReorderFaqRequest;
import br.com.condominio.feature.faq.dto.UpdateFaqRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FAQ do condomínio. Leitura pública (publicados); escrita requer {@code FAQ_MANAGE} no controller.
 */
@Service
@RequiredArgsConstructor
public class FaqService {

  private final FaqRepository repo;
  private final ActivityNotifier activityNotifier;

  @Transactional(readOnly = true)
  public List<FaqView> listPublished() {
    return repo.findAllByPublishedTrueOrderByCategoryAscOrderingAsc().stream()
        .map(FaqView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FaqView> listAll() {
    return repo.findAllByOrderByCategoryAscOrderingAsc().stream().map(FaqView::of).toList();
  }

  @Transactional(readOnly = true)
  public FaqView getById(UUID id) {
    return FaqView.of(find(id));
  }

  @Transactional
  public FaqView create(CreateFaqRequest b) {
    Integer max = repo.findMaxOrderingByCategory(b.category());
    int nextOrdering = (max == null ? 0 : max + 1);
    Faq f =
        repo.save(Faq.create(b.question(), b.answer(), b.category(), b.published(), nextOrdering));
    activityNotifier.notify(ActivityAction.CREATED, "FAQ", b.question(), null);
    return FaqView.of(f);
  }

  @Transactional
  public FaqView update(UUID id, UpdateFaqRequest b) {
    Faq f = find(id);
    f.edit(b.question(), b.answer(), b.category());
    f.setPublishedFlag(b.published());
    activityNotifier.notify(ActivityAction.UPDATED, "FAQ", f.getQuestion(), null);
    return FaqView.of(f);
  }

  @Transactional
  public FaqView setPublished(UUID id, boolean published) {
    Faq f = find(id);
    f.setPublishedFlag(published);
    return FaqView.of(f);
  }

  @Transactional
  public void reorder(List<ReorderFaqRequest.Item> items) {
    for (ReorderFaqRequest.Item it : items) {
      find(it.id()).setOrderingValue(it.ordering());
    }
  }

  @Transactional
  public void delete(UUID id) {
    Faq f = find(id);
    activityNotifier.notify(ActivityAction.DELETED, "FAQ", f.getQuestion(), null);
    repo.delete(f);
  }

  private Faq find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new FaqException("NOT_FOUND", "FAQ não encontrado."));
  }
}
