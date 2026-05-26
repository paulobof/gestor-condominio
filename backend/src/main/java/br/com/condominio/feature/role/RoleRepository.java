package br.com.condominio.feature.role;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {
  Optional<Role> findByName(RoleName name);
}
