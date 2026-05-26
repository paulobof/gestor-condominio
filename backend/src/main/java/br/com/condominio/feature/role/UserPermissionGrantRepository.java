package br.com.condominio.feature.role;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionGrantRepository extends JpaRepository<UserPermissionGrant, UUID> {

  List<UserPermissionGrant> findByUserIdAndRevokedAtIsNull(UUID userId);
}
