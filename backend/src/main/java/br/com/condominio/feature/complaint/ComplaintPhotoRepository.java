package br.com.condominio.feature.complaint;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComplaintPhotoRepository extends JpaRepository<ComplaintPhoto, UUID> {

  List<ComplaintPhoto> findByComplaintIdInOrderByOrdering(Collection<UUID> complaintIds);

  List<ComplaintPhoto> findByComplaintIdOrderByOrdering(UUID complaintId);

  long countByComplaintId(UUID complaintId);

  Optional<ComplaintPhoto> findByIdAndComplaintId(UUID id, UUID complaintId);

  @Query("select coalesce(max(p.ordering), -1) from ComplaintPhoto p where p.complaintId = :id")
  int maxOrdering(UUID id);
}
