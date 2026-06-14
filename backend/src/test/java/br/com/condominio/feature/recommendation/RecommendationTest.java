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
        "ótimo",
        null,
        null,
        null,
        null,
        null,
        null);
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
        "ótimo",
        null,
        null,
        null,
        null,
        null,
        null);
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
  void create_resident_souEu_nullResidentId_isActive() {
    // "sou eu" path: resident=true com residentId=null é válido na entidade;
    // a guarda de negócio (usuário sem unidade) fica no Service.
    Recommendation r =
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
            "ok",
            null,
            null,
            null,
            null,
            UUID.randomUUID(),
            "T1-A-101");
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
    assertThat(r.getOwnerUnitCode()).isEqualTo("T1-A-101");
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
    r.edit(
        "Eletricista",
        "Maria",
        "11888887777",
        "Rua Y",
        "R$120/h",
        4,
        "boa",
        "https://instagram.com/maria",
        null,
        null,
        null);
    assertThat(r.getServiceName()).isEqualTo("Eletricista");
    assertThat(r.getRating()).isEqualTo((short) 4);
    assertThat(r.getInstagramUrl()).isEqualTo("https://instagram.com/maria");
  }

  @Test
  void edit_withNullRating_keepsNull() {
    Recommendation r = external();
    r.edit("Pintor", "João", "11999990000", "Rua X", "R$80/h", null, "ok", null, null, null, null);
    assertThat(r.getRating()).isNull();
  }
}
