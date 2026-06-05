package br.com.condominio.feature.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, UUID> {
  Optional<Tag> findBySlug(String slug);

  @Query("select t from Tag t where lower(t.slug) like lower(concat(:q, '%')) order by t.slug")
  List<Tag> searchBySlugPrefix(String q);
}
