package br.com.condominio.feature.parkingrental;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingRentalRepository extends JpaRepository<ParkingRental, UUID> {
  Page<ParkingRental> findByStatus(ParkingRentalStatus status, Pageable pageable);
}
