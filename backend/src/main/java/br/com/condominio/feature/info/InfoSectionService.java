package br.com.condominio.feature.info;

import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.InfoSectionView;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
import br.com.condominio.shared.html.HtmlSanitizer;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informações gerais: seções livres em rich text. Leitura por autenticados; escrita requer {@code
 * INFO_MANAGE} (checado no controller). O corpo é sanitizado na escrita (XSS — STRIDE).
 */
@Service
@RequiredArgsConstructor
public class InfoSectionService {

  private final InfoSectionRepository repo;
  private final HtmlSanitizer sanitizer;

  @Transactional(readOnly = true)
  public List<InfoSectionView> list() {
    return repo.findAllByOrderByPositionAsc().stream().map(InfoSectionView::of).toList();
  }

  @Transactional
  public InfoSectionView create(CreateInfoSectionRequest b) {
    Integer max = repo.findMaxPosition();
    int next = (max == null ? 0 : max + 1);
    InfoSection s = InfoSection.create(b.title(), sanitizer.sanitize(b.body()), next);
    return InfoSectionView.of(repo.save(s));
  }

  @Transactional
  public InfoSectionView update(UUID id, UpdateInfoSectionRequest b) {
    InfoSection s = find(id);
    s.edit(b.title(), sanitizer.sanitize(b.body()));
    return InfoSectionView.of(s);
  }

  @Transactional
  public void reorder(List<ReorderInfoRequest.Item> items) {
    for (ReorderInfoRequest.Item it : items) {
      find(it.id()).moveTo(it.position());
    }
  }

  @Transactional
  public void delete(UUID id) {
    repo.delete(find(id));
  }

  private InfoSection find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new InfoException("NOT_FOUND", "Seção não encontrada."));
  }
}
