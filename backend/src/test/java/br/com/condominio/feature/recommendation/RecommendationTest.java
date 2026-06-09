package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationTest {

  private Recommendation external() {
    return Recommendation.create(
        UUID.randomUUID(),
        "Pintor",
        "João",
        "11999990000",
        false,
        null,
        "Rua X",
        "R$80/h",
        5,
        "ótimo");
  }

  private Recommendation resident(UUID residentId) {
    return Recommendation.create(
        UUID.randomUUID(),
        "Pintor",
        "João",
        "11999990000",
        true,
        residentId,
        "Apto 101",
        "R$80/h",
        5,
        "ótimo");
  }

  @Test
  void create_external_isActive() {
    assertThat(external().getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void create_resident_isActive() {
    // Indicações de morador não exigem mais aprovação: entram ACTIVE direto.
    assertThat(resident(UUID.randomUUID()).getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
  }

  @Test
  void create_resident_withoutResidentId_throws() {
    assertThatThrownBy(
            () ->
                Recommendation.create(
                    UUID.randomUUID(),
                    "Pintor",
                    "João",
                    "11999990000",
                    true,
                    null,
                    "Apto",
                    "R$80/h",
                    5,
                    "ok"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hide_setsHidden() {
    Recommendation r = external();
    r.hide();
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.HIDDEN);
  }

  @Test
  void hide_whenAlreadyHidden_throws() {
    Recommendation r = external();
    r.hide();
    assertThatThrownBy(r::hide).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void edit_updatesFields_andNarrowsRating() {
    Recommendation r = external();
    r.edit("Eletricista", "Maria", "11888887777", "Rua Y", "R$120/h", 4, "boa");
    assertThat(r.getServiceName()).isEqualTo("Eletricista");
    assertThat(r.getRating()).isEqualTo((short) 4);
  }

  @Test
  void edit_withNullRating_keepsNull() {
    Recommendation r = external();
    r.edit("Pintor", "João", "11999990000", "Rua X", "R$80/h", null, "ok");
    assertThat(r.getRating()).isNull();
  }
}
