package br.com.condominio.feature.complaint.batch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintBatchRepository extends JpaRepository<ComplaintBatch, UUID> {
  List<ComplaintBatch> findAllByOrderBySentAtDesc();
}
