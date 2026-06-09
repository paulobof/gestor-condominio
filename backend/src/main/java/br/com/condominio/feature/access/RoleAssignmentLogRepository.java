package br.com.condominio.feature.access;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentLogRepository extends JpaRepository<RoleAssignmentLog, UUID> {}
