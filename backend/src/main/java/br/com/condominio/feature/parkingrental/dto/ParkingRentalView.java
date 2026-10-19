package br.com.condominio.feature.parkingrental.dto;

import br.com.condominio.feature.parkingrental.ParkingRental;
import br.com.condominio.feature.parkingrental.ParkingRentalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParkingRentalView(
    UUID id,
    String tower,
    String floor,
    String spotNumber,
    BigDecimal monthlyPrice,
    ParkingRentalStatus status,
    UUID authorUserId,
    Instant createdAt,
    String authorName,
    String contactPhone,
    String contactWhatsapp) {

  public static ParkingRentalView of(ParkingRental r, String authorName, String contactWhatsapp) {
    return new ParkingRentalView(
        r.getId(),
        r.getTower(),
        r.getFloor(),
        r.getSpotNumber(),
        r.getMonthlyPrice(),
        r.getStatus(),
        r.getAuthorUserId(),
        r.getCreatedAt(),
        authorName,
        r.getContactPhone(),
        contactWhatsapp);
  }
}
