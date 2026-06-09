package br.com.condominio.feature.contact;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactOpeningHoursRepository extends JpaRepository<ContactOpeningHours, UUID> {
  List<ContactOpeningHours> findByOwnerIdOrderByDayOfWeekAsc(UUID ownerId);

  void deleteByOwnerId(UUID ownerId);
}
