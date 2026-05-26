package br.com.condominio.feature.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByIdAndStatus(UUID id, UserStatus status);

  @Query(
      "SELECT u FROM User u WHERE u.status = br.com.condominio.feature.user.UserStatus.PENDING_APPROVAL "
          + "AND u.isUnitMaster = true ORDER BY u.createdAt DESC")
  Page<User> findPendingMasters(Pageable pageable);
}
