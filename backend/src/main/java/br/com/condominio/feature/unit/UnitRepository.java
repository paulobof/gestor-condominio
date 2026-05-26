package br.com.condominio.feature.unit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
  Optional<Unit> findByCode(String code);
}
