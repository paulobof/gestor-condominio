package br.com.condominio.feature.complaint.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintTypeRepository extends JpaRepository<ComplaintType, UUID> {
  List<ComplaintType> findAllByOrderBySortOrderAscNameAsc();

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
