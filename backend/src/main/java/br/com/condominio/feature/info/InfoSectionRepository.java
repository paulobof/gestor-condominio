package br.com.condominio.feature.info;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InfoSectionRepository extends JpaRepository<InfoSection, UUID> {

  List<InfoSection> findAllByOrderByPositionAsc();

  @Query("select max(s.position) from InfoSection s")
  Integer findMaxPosition();
}
