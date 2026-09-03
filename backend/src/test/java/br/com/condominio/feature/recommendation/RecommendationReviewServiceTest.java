package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.recommendation.dto.ReviewRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationReviewServiceTest {

  private static final UUID RID = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID REVIEW_ID = UUID.randomUUID();
  private static final UUID OTHER_USER = UUID.randomUUID();

  private RecommendationRepository repo;
  private RecommendationReviewRepository reviewRepo;
  private RecommendationReviewService service;
  private Recommendation rec;

  @BeforeEach
  void setUp() {
    repo = mock(RecommendationRepository.class);
    reviewRepo = mock(RecommendationReviewRepository.class);
    service = new RecommendationReviewService(repo, reviewRepo);

    rec =
        Recommendation.create(
            UUID.randomUUID(),
            "Pintor",
            "João",
            "11999990000",
            false,
            null,
            "Rua X",
            "R$80/h",
            5,
            "ok",
            null,
            null,
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(rec, "id", RID);
    when(repo.findById(RID)).thenReturn(Optional.of(rec));
    when(repo.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));
    when(reviewRepo.save(any(RecommendationReview.class))).thenAnswer(i -> i.getArgument(0));
  }

  private ReviewRequest req(ReviewSentiment sentiment, int rating, String text) {
    return new ReviewRequest(sentiment, rating, text);
  }

  private RecommendationReview review(ReviewSentiment sentiment, int rating) {
    return RecommendationReview.create(
        RID, UNIT, ACTOR, sentiment, (short) rating, "Comentário com dez ou mais caracteres.");
  }

  private RecommendationReview hiddenReview(ReviewSentiment sentiment, int rating) {
    RecommendationReview r = review(sentiment, rating);
    r.hide();
    return r;
  }

  private RecommendationReview existingReview() {
    return review(ReviewSentiment.LIKE, 4);
  }

  private RecommendationReview reviewBy(UUID authorId) {
    return RecommendationReview.create(
        RID,
        UNIT,
        authorId,
        ReviewSentiment.LIKE,
        (short) 5,
        "Comentário com dez ou mais caracteres.");
  }

  @Test
  void create_semUnidade_lancaForbidden() {
    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, null, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_REQUIRES_UNIT");
  }

  @Test
  void create_apartamentoJaAvaliou_lancaErroDeDominio() {
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT))
        .thenReturn(Optional.of(existingReview()));

    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, UNIT, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_UNIT_ALREADY_REVIEWED");
  }

  @Test
  void create_atualizaOsQuatroContadores() {
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(review(ReviewSentiment.LIKE, 5), review(ReviewSentiment.DISLIKE, 2)));

    service.create(RID, ACTOR, UNIT, req(ReviewSentiment.LIKE, 5, "Serviço muito bom."));

    assertThat(rec.getLikeCount()).isEqualTo(1);
    assertThat(rec.getDislikeCount()).isEqualTo(1);
    assertThat(rec.getReviewCount()).isEqualTo(2);
    assertThat(rec.getRatingSum()).isEqualTo(7);
  }

  @Test
  void contadores_ignoramAvaliacaoOculta() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(
            List.of(review(ReviewSentiment.LIKE, 5), hiddenReview(ReviewSentiment.DISLIKE, 1)));

    service.recountAndSave(rec);

    assertThat(rec.getReviewCount()).isEqualTo(1);
    assertThat(rec.getRatingSum()).isEqualTo(5);
    assertThat(rec.getDislikeCount()).isZero();
  }

  @Test
  void edit_deOutroAutor_lancaForbidden() {
    when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID))
        .thenReturn(Optional.of(reviewBy(OTHER_USER)));

    assertThatThrownBy(
            () ->
                service.edit(
                    RID, REVIEW_ID, ACTOR, req(ReviewSentiment.LIKE, 4, "Mudei de ideia depois.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void delete_daPropriaAvaliacao_apagaERecontabiliza() {
    when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID))
        .thenReturn(Optional.of(reviewBy(ACTOR)));

    service.delete(RID, REVIEW_ID, ACTOR);

    verify(reviewRepo).delete(any(RecommendationReview.class));
    assertThat(rec.getReviewCount()).isZero();
  }
}
