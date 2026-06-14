package br.com.condominio.feature.unit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
  Optional<Unit> findByCode(String code);

  Optional<Unit> findByMasterUserId(UUID masterUserId);

  @Query("SELECT u.code FROM Unit u WHERE u.id = :id")
  Optional<String> findCodeById(@Param("id") UUID id);
}
