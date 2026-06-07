package br.com.condominio.feature.announcement;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  /**
   * Feed do mural: fixados primeiro, depois mais recentes. {@code @SQLRestriction} filtra
   * deletados.
   */
  Page<Announcement> findAllByOrderByPinnedDescPublishedAtDesc(Pageable pageable);
}
