package br.com.condominio.feature.classified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassifiedTest {

  private Classified active() {
    return Classified.create(UUID.randomUUID(), "Bicicleta", "Aro 29", new BigDecimal("500.00"));
  }

  @Test
  void create_startsActive() {
    assertThat(active().getStatus()).isEqualTo(ClassifiedStatus.ACTIVE);
  }

  @Test
  void markSold_fromActive_becomesSold() {
    Classified c = active();
    c.markSold();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.SOLD);
  }

  @Test
  void markSold_whenNotActive_throws() {
    Classified c = active();
    c.markSold();
    assertThatThrownBy(c::markSold).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archive_fromActive_becomesArchived() {
    Classified c = active();
    c.archive();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.ARCHIVED);
  }

  @Test
  void archive_whenAlreadyArchived_throws() {
    Classified c = active();
    c.archive();
    assertThatThrownBy(c::archive).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reactivate_fromArchived_becomesActive() {
    Classified c = active();
    c.archive();
    c.reactivate();
    assertThat(c.getStatus()).isEqualTo(ClassifiedStatus.ACTIVE);
  }

  @Test
  void edit_updatesFields() {
    Classified c = active();
    c.edit("Bike", "Nova descrição", new BigDecimal("600.00"));
    assertThat(c.getTitle()).isEqualTo("Bike");
    assertThat(c.getPrice()).isEqualByComparingTo("600.00");
  }
}
