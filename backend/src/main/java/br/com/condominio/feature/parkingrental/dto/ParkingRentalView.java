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
    String authorPhone,
    String authorWhatsapp) {

  public static ParkingRentalView of(
      ParkingRental r, String authorName, String authorPhone, String authorWhatsapp) {
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
        authorPhone,
        authorWhatsapp);
  }
}
