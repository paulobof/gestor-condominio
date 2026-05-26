package br.com.condominio.feature.role;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
  List<UserRole> findById_UserId(UUID userId);
}
