package br.com.condominio.feature.parkingrental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "parking_rental")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "status"})
@SQLDelete(sql = "UPDATE parking_rental SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ParkingRental {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(nullable = false, length = 40)
  private String tower;

  @Column(nullable = false, length = 20)
  private String floor;

  @Column(name = "spot_number", nullable = false, length = 40)
  private String spotNumber;

  @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ParkingRentalStatus status;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static ParkingRental create(
      UUID authorUserId, String tower, String floor, String spotNumber, BigDecimal monthlyPrice) {
    ParkingRental r = new ParkingRental();
    r.authorUserId = authorUserId;
    r.tower = tower;
    r.floor = floor;
    r.spotNumber = spotNumber;
    r.monthlyPrice = monthlyPrice;
    r.status = ParkingRentalStatus.ACTIVE;
    return r;
  }

  public void edit(String tower, String floor, String spotNumber, BigDecimal monthlyPrice) {
    this.tower = tower;
    this.floor = floor;
    this.spotNumber = spotNumber;
    this.monthlyPrice = monthlyPrice;
  }

  public void markRented() {
    if (status != ParkingRentalStatus.ACTIVE) {
      throw new IllegalStateException("Só anúncios ativos podem ser marcados como alugados.");
    }
    status = ParkingRentalStatus.RENTED;
  }

  public void archive() {
    if (status == ParkingRentalStatus.ARCHIVED) {
      throw new IllegalStateException("Anúncio já está arquivado.");
    }
    status = ParkingRentalStatus.ARCHIVED;
  }

  public void reactivate() {
    if (status == ParkingRentalStatus.ACTIVE) {
      throw new IllegalStateException("Anúncio já está ativo.");
    }
    status = ParkingRentalStatus.ACTIVE;
  }
}
