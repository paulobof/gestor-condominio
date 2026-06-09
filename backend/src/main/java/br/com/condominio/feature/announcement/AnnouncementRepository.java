package br.com.condominio.feature.announcement;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  /** Feed do mural ordenado por posição manual. {@code @SQLRestriction} filtra deletados. */
  Page<Announcement> findAllByOrderByPositionAsc(Pageable pageable);

  @Query("select min(a.position) from Announcement a")
  Integer findMinPosition();
}
