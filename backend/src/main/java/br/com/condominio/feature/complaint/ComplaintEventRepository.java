package br.com.condominio.feature.complaint;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintEventRepository extends JpaRepository<ComplaintEvent, UUID> {
  List<ComplaintEvent> findByComplaintIdOrderByCreatedAtAsc(UUID complaintId);
}
