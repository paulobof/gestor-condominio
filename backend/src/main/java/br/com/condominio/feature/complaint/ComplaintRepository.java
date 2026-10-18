package br.com.condominio.feature.complaint;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComplaintRepository
    extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {
  List<Complaint> findByAuthorUserIdOrderByCreatedAtDesc(UUID authorUserId);
}
