package br.com.condominio.feature.role;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Short> {
  Optional<Permission> findByCode(PermissionCode code);
}
