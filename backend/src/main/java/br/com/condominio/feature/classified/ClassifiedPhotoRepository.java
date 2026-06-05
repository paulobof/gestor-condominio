package br.com.condominio.feature.classified;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClassifiedPhotoRepository extends JpaRepository<ClassifiedPhoto, UUID> {

  List<ClassifiedPhoto> findByClassifiedIdOrderByOrdering(UUID classifiedId);

  long countByClassifiedId(UUID classifiedId);

  Optional<ClassifiedPhoto> findByIdAndClassifiedId(UUID id, UUID classifiedId);

  @Query("select coalesce(max(p.ordering), -1) from ClassifiedPhoto p where p.classifiedId = :id")
  int maxOrdering(UUID id);
}
