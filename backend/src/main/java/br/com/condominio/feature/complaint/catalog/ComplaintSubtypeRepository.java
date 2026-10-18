package br.com.condominio.feature.complaint.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintSubtypeRepository extends JpaRepository<ComplaintSubtype, UUID> {
  List<ComplaintSubtype> findAllByOrderBySortOrderAscNameAsc();

  List<ComplaintSubtype> findByTypeIdOrderBySortOrderAscNameAsc(UUID typeId);

  boolean existsByTypeIdAndNameIgnoreCase(UUID typeId, String name);

  boolean existsByTypeIdAndNameIgnoreCaseAndIdNot(UUID typeId, String name, UUID id);
}
