package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationOpeningHoursRepository
    extends JpaRepository<RecommendationOpeningHours, UUID> {
  List<RecommendationOpeningHours> findByOwnerIdOrderByDayOfWeek(UUID ownerId);

  void deleteByOwnerId(UUID ownerId);
}
