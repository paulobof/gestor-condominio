package br.com.condominio.feature.password;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

  /** Últimas N entries do usuário ordenadas por createdAt desc. Usado para checar reuso. */
  List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}
