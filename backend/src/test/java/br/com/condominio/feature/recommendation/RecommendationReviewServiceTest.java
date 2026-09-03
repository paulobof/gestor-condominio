package br.com.condominio.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.recommendation.dto.ReviewRequest;
import br.com.condominio.feature.recommendation.dto.ReviewView;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
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
  private static final UUID OUTRA_UNIT = UUID.randomUUID();
  private static final UUID REVIEW_ID = UUID.randomUUID();
  private static final UUID OTHER_USER = UUID.randomUUID();

  private RecommendationRepository repo;
  private RecommendationReviewRepository reviewRepo;
  private UserRepository userRepo;
  private UnitRepository unitRepo;
  private RecommendationReviewService service;
  private Recommendation rec;

  @BeforeEach
  void setUp() {
    repo = mock(RecommendationRepository.class);
    reviewRepo = mock(RecommendationReviewRepository.class);
    userRepo = mock(UserRepository.class);
    unitRepo = mock(UnitRepository.class);
    service = new RecommendationReviewService(repo, reviewRepo, userRepo, unitRepo);

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
    when(userRepo.findAllById(any())).thenReturn(List.of(userWith(OTHER_USER, "Ana Moradora")));
    when(unitRepo.findAllById(any())).thenReturn(List.of(unitWith(UNIT, "402A")));
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

  private RecommendationReview reviewBy(UUID authorId, UUID unitId) {
    return RecommendationReview.create(
        RID,
        unitId,
        authorId,
        ReviewSentiment.LIKE,
        (short) 5,
        "Comentário com dez ou mais caracteres.");
  }

  private User userWith(UUID id, String fullName) {
    User u = newInstance(User.class);
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "fullName", fullName);
    return u;
  }

  private Unit unitWith(UUID id, String code) {
    Unit u = newInstance(Unit.class);
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "code", code);
    return u;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var c = type.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
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
        .thenReturn(Optional.of(reviewBy(OTHER_USER, UNIT)));

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
        .thenReturn(Optional.of(reviewBy(ACTOR, UNIT)));

    service.delete(RID, REVIEW_ID, ACTOR);

    verify(reviewRepo).delete(any(RecommendationReview.class));
    assertThat(rec.getReviewCount()).isZero();
  }

  @Test
  void list_paraQualquerUm_naoRevelaAutorNemApartamento() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    List<ReviewView> views = service.list(RID, ACTOR, OUTRA_UNIT, false);

    assertThat(views).hasSize(1);
    assertThat(views.get(0).authorName()).isNull();
    assertThat(views.get(0).unitCode()).isNull();
    assertThat(views.get(0).text()).isEqualTo("Comentário com dez ou mais caracteres.");
  }

  @Test
  void list_paraModerador_revelaAutorEApartamento() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    List<ReviewView> views = service.list(RID, ACTOR, UNIT, true);

    assertThat(views.get(0).authorName()).isEqualTo("Ana Moradora");
    assertThat(views.get(0).unitCode()).isEqualTo("402A");
  }

  @Test
  void list_marcaAPropriaAvaliacaoComoMine() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(ACTOR, UNIT)));

    assertThat(service.list(RID, ACTOR, UNIT, false).get(0).mine()).isTrue();
  }

  @Test
  void list_paraVizinhoDoMesmoApartamento_revelaQuemAvaliou() {
    // O segundo morador precisa saber que a Ana avaliou, senao so ve "voce nao pode avaliar".
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT))); // OTHER_USER mora na mesma UNIT

    List<ReviewView> views = service.list(RID, ACTOR, UNIT, false);

    assertThat(views.get(0).authorName()).isEqualTo("Ana Moradora");
    assertThat(views.get(0).mine()).isFalse();
  }

  @Test
  void list_paraMoradorDeOutroApartamento_naoRevelaQuemAvaliou() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT))); // avaliacao da UNIT

    List<ReviewView> views = service.list(RID, ACTOR, OUTRA_UNIT, false);

    assertThat(views.get(0).authorName()).isNull();
    assertThat(views.get(0).unitCode()).isNull();
  }

  @Test
  void list_naoDevolveOcultas_paraQuemNaoModera() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(hiddenReview(ReviewSentiment.DISLIKE, 1)));

    assertThat(service.list(RID, ACTOR, UNIT, false)).isEmpty();
  }

  @Test
  void hide_ocultaSemLiberarAVagaDoApartamento() {
    RecommendationReview r = reviewBy(OTHER_USER, UNIT);
    when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID)).thenReturn(Optional.of(r));

    service.hide(RID, REVIEW_ID);

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    verify(reviewRepo, never()).delete(any());
    assertThat(rec.getReviewCount()).isZero();
  }
}
