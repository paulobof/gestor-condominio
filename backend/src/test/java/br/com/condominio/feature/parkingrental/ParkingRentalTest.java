package br.com.condominio.feature.parkingrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParkingRentalTest {

  private ParkingRental sample() {
    return ParkingRental.create(UUID.randomUUID(), "A", "-1", "045", new BigDecimal("350.00"));
  }

  @Test
  void create_startsActive() {
    ParkingRental r = sample();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ACTIVE);
    assertThat(r.getTower()).isEqualTo("A");
    assertThat(r.getFloor()).isEqualTo("-1");
    assertThat(r.getSpotNumber()).isEqualTo("045");
    assertThat(r.getMonthlyPrice()).isEqualByComparingTo("350.00");
  }

  @Test
  void edit_updatesAllFields() {
    ParkingRental r = sample();
    r.edit("B", "2", "B-200", new BigDecimal("500.00"));
    assertThat(r.getTower()).isEqualTo("B");
    assertThat(r.getFloor()).isEqualTo("2");
    assertThat(r.getSpotNumber()).isEqualTo("B-200");
    assertThat(r.getMonthlyPrice()).isEqualByComparingTo("500.00");
  }

  @Test
  void markRented_fromActive_succeeds() {
    ParkingRental r = sample();
    r.markRented();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.RENTED);
  }

  @Test
  void markRented_whenNotActive_throws() {
    ParkingRental r = sample();
    r.archive();
    assertThatThrownBy(r::markRented).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_thenReactivate_returnsToActive() {
    ParkingRental r = sample();
    r.archive();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ARCHIVED);
    r.reactivate();
    assertThat(r.getStatus()).isEqualTo(ParkingRentalStatus.ACTIVE);
  }

  @Test
  void reactivate_whenAlreadyActive_throws() {
    ParkingRental r = sample();
    assertThatThrownBy(r::reactivate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_whenAlreadyArchived_throws() {
    ParkingRental r = sample();
    r.archive();
    assertThatThrownBy(r::archive).isInstanceOf(IllegalStateException.class);
  }
}
