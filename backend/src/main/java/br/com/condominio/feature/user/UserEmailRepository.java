package br.com.condominio.feature.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserEmailRepository extends JpaRepository<UserEmail, UUID> {

  @Query("SELECT ue FROM UserEmail ue WHERE LOWER(ue.email) = LOWER(:email)")
  Optional<UserEmail> findActiveByEmailIgnoreCase(String email);

  List<UserEmail> findByUserId(UUID userId);
}
