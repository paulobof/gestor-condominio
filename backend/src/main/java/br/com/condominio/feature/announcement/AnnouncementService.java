package br.com.condominio.feature.announcement;

import br.com.condominio.feature.announcement.dto.AnnouncementView;
import br.com.condominio.feature.announcement.dto.CreateAnnouncementRequest;
import br.com.condominio.feature.announcement.dto.UpdateAnnouncementRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mural de avisos. Leitura liberada a qualquer autenticado; escrita exigida via {@code
 * ANNOUNCEMENT_MANAGE} no controller (só o síndico publica), por isso o service não repete a
 * checagem de autorização.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

  private final AnnouncementRepository repo;

  @Transactional(readOnly = true)
  public Page<AnnouncementView> list(Pageable pageable) {
    return repo.findAllByOrderByPositionAsc(pageable).map(AnnouncementView::of);
  }

  @Transactional(readOnly = true)
  public AnnouncementView getById(UUID id) {
    return AnnouncementView.of(find(id));
  }

  @Transactional
  public AnnouncementView create(UUID authorId, CreateAnnouncementRequest body) {
    Integer min = repo.findMinPosition();
    int top = (min == null ? 0 : min - 1);
    Announcement a = Announcement.create(authorId, body.title(), body.body(), top);
    return AnnouncementView.of(repo.save(a));
  }

  @Transactional
  public AnnouncementView update(UUID id, UpdateAnnouncementRequest body) {
    Announcement a = find(id);
    a.edit(body.title(), body.body());
    return AnnouncementView.of(a);
  }

  @Transactional
  public void delete(UUID id) {
    repo.delete(find(id));
  }

  private Announcement find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new AnnouncementException("NOT_FOUND", "Aviso não encontrado."));
  }
}
