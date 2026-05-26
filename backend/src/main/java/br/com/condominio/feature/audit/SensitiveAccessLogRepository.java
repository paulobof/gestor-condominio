package br.com.condominio.feature.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensitiveAccessLogRepository extends JpaRepository<SensitiveAccessLog, UUID> {}
