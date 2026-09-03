package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationReviewTest {

  private static final UUID RID = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID AUTHOR = UUID.randomUUID();

  @Test
  void create_nasceAtivaEComOTextoAparado() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "  Serviço impecável.  ");

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
    assertThat(r.getText()).isEqualTo("Serviço impecável.");
    assertThat(r.getRecommendationId()).isEqualTo(RID);
    assertThat(r.getUnitId()).isEqualTo(UNIT);
    assertThat(r.getAuthorUserId()).isEqualTo(AUTHOR);
  }

  @Test
  void edit_trocaSentimentoNotaETexto() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "Serviço impecável.");

    r.edit(ReviewSentiment.DISLIKE, (short) 2, "Voltou e refez errado.");

    assertThat(r.getSentiment()).isEqualTo(ReviewSentiment.DISLIKE);
    assertThat(r.getRating()).isEqualTo((short) 2);
    assertThat(r.getText()).isEqualTo("Voltou e refez errado.");
  }

  @Test
  void hide_ocultaSemApagar() {
    RecommendationReview r =
        RecommendationReview.create(
            RID, UNIT, AUTHOR, ReviewSentiment.LIKE, (short) 5, "Serviço impecável.");

    r.hide();

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
  }
}
