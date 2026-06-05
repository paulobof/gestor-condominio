package br.com.condominio.feature.tag;

import br.com.condominio.feature.tag.dto.TagView;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagService {

  private final TagRepository repo;

  @Transactional
  public Tag getOrCreate(String rawSlug, String label) {
    String slug = normalize(rawSlug);
    return repo.findBySlug(slug)
        .orElseGet(
            () ->
                repo.save(
                    Tag.create(
                        slug,
                        label == null || label.isBlank() ? defaultLabel(rawSlug) : label,
                        null)));
  }

  public List<TagView> searchForAutocomplete(String q) {
    if (q == null || q.isBlank()) return List.of();
    return repo.searchBySlugPrefix(normalize(q)).stream().map(TagView::of).toList();
  }

  @Transactional
  public void delete(UUID id) {
    repo.findById(id).ifPresent(repo::delete);
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().toLowerCase();
  }

  private static String defaultLabel(String raw) {
    String r = raw == null ? "" : raw.trim();
    return r.isEmpty() ? r : Character.toUpperCase(r.charAt(0)) + r.substring(1);
  }
}
