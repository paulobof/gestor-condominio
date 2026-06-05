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
  void create_resident_isPendingConsent() {
    assertThat(resident(UUID.randomUUID()).getStatus())
        .isEqualTo(RecommendationStatus.PENDING_RESIDENT_CONSENT);
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
  void consentByResident_fromPending_becomesActive() {
    Recommendation r = resident(UUID.randomUUID());
    r.consentByResident();
    assertThat(r.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
    assertThat(r.getResidentConsentAt()).isNotNull();
  }

  @Test
  void consentByResident_whenNotPending_throws() {
    Recommendation r = external();
    assertThatThrownBy(r::consentByResident).isInstanceOf(IllegalStateException.class);
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
}
