package br.com.condominio.feature.classified;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassifiedRepository extends JpaRepository<Classified, UUID> {
  Page<Classified> findByStatus(ClassifiedStatus status, Pageable pageable);
}
