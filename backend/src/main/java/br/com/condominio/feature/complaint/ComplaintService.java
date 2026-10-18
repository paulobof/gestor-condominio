package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.complaint.dto.ComplaintView;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintService {

  private final ComplaintRepository repo;
  private final ComplaintCatalogService catalog;
  private final ComplaintEventWriter events;
  private final ComplaintViewAssembler views;

  /** saveAndFlush: o {@code code} vem da sequence no INSERT e precisa estar na resposta. */
  @Transactional
  public ComplaintView create(UUID userId, UUID unitId, ComplaintFields f) {
    if (unitId == null) {
      throw new ComplaintException("NO_UNIT", "Só moradores com unidade podem abrir chamados.");
    }
    catalog.requireUsable(f.typeId(), f.subtypeId(), f.locationId(), Set.of());
    Complaint c = repo.saveAndFlush(Complaint.create(userId, unitId, f));
    events.flush(c);
    return views.toView(c, userId);
  }

  @Transactional(readOnly = true)
  public List<ComplaintView> mine(UUID userId) {
    return views.toViews(repo.findByAuthorUserIdOrderByCreatedAtDesc(userId), userId);
  }

  @Transactional(readOnly = true)
  public ComplaintView get(UUID id, UUID userId, boolean canManage) {
    Complaint c = load(id);
    if (!c.isVisibleTo(userId, canManage)) throw notFound();
    return views.toView(c, userId);
  }

  @Transactional
  public ComplaintView update(UUID id, UUID userId, ComplaintFields f) {
    Complaint c = load(id);
    if (!c.isAuthor(userId)) throw notFound();
    catalog.requireUsable(f.typeId(), f.subtypeId(), f.locationId(), c.usedCatalogIds());
    c.editByAuthor(userId, f);
    events.flush(c);
    return views.toView(c, userId);
  }

  @Transactional
  public void cancel(UUID id, UUID userId) {
    Complaint c = load(id);
    c.cancelByAuthor(userId);
    events.flush(c);
    repo.delete(c);
  }

  Complaint load(UUID id) {
    return repo.findById(id).orElseThrow(ComplaintService::notFound);
  }

  static ComplaintException notFound() {
    return new ComplaintException("NOT_FOUND", "Chamado não encontrado.");
  }
}
