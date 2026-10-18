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
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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
    when(reviewRepo.saveAndFlush(any(RecommendationReview.class)))
        .thenAnswer(i -> i.getArgument(0));
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
                    RID, ACTOR, null, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_REQUIRES_UNIT");
  }

  @Test
  void create_textoCurto_lancaErroDeDominio() {
    // Caminho interno (RecommendationService.create monta o ReviewRequest na mão, sem @Valid):
    // sem esta checagem de dominio, um texto curto so seria barrado pela CHECK do banco (500).
    assertThatThrownBy(
            () -> service.create(RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "curto")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_INVALID");
    verify(reviewRepo, never()).saveAndFlush(any());
  }

  @Test
  void create_notaForaDeEscala_lancaErroDeDominio() {
    assertThatThrownBy(
            () ->
                service.create(
                    RID,
                    ACTOR,
                    UNIT,
                    false,
                    req(ReviewSentiment.LIKE, 6, "Comentário com dez ou mais caracteres.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_INVALID");
    verify(reviewRepo, never()).saveAndFlush(any());
  }

  @Test
  void create_apartamentoJaAvaliou_lancaErroDeDominio() {
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT))
        .thenReturn(Optional.of(existingReview()));

    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_UNIT_ALREADY_REVIEWED");
  }

  @Test
  void create_atualizaOsQuatroContadores() {
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(review(ReviewSentiment.LIKE, 5), review(ReviewSentiment.DISLIKE, 2)));

    service.create(RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom."));

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
                    RID,
                    REVIEW_ID,
                    ACTOR,
                    false,
                    req(ReviewSentiment.LIKE, 4, "Mudei de ideia depois.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void edit_textoCurto_lancaErroDeDominio() {
    assertThatThrownBy(
            () -> service.edit(RID, REVIEW_ID, ACTOR, false, req(ReviewSentiment.LIKE, 5, "curto")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_INVALID");
    verify(reviewRepo, never()).save(any());
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
    // actorUnitId = OUTRA_UNIT (nao a da avaliacao): isola a revelacao por permissao de
    // moderacao, sem se apoiar na revelacao por "mesma unidade" tambem.
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    List<ReviewView> views = service.list(RID, ACTOR, OUTRA_UNIT, true);

    assertThat(views.get(0).authorName()).isEqualTo("Ana Moradora");
    assertThat(views.get(0).unitCode()).isEqualTo("402A");
    // authorName veio por moderacao, nao por unidade: sameUnit tem que ficar falso, senao um
    // morador-moderador cujo proprio apartamento nao avaliou perde o formulario na tela.
    assertThat(views.get(0).sameUnit()).isFalse();
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
    assertThat(views.get(0).sameUnit()).isTrue();
  }

  @Test
  void list_paraMoradorDeOutroApartamento_naoRevelaQuemAvaliou() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT))); // avaliacao da UNIT

    List<ReviewView> views = service.list(RID, ACTOR, OUTRA_UNIT, false);

    assertThat(views.get(0).authorName()).isNull();
    assertThat(views.get(0).unitCode()).isNull();
    assertThat(views.get(0).sameUnit()).isFalse();
  }

  @Test
  void list_naoDevolveOcultas_paraQuemNaoModera() {
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(hiddenReview(ReviewSentiment.DISLIKE, 1)));

    assertThat(service.list(RID, ACTOR, UNIT, false)).isEmpty();
  }

  @Test
  void list_semNinguemComDireitoDeVer_naoConsultaUsuarioNemUnidade() {
    // Ninguem naquela chamada tem direito ao nome/unidade: nao ha por que trafegar PII de
    // ninguem, entao os repositorios de usuario e unidade nem devem ser acionados.
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    service.list(RID, ACTOR, OUTRA_UNIT, false);

    verify(userRepo, never()).findAllById(any());
    verify(unitRepo, never()).findAllById(any());
  }

  @Test
  void list_buscaEmLoteApenasOsIdsDasAvaliacoesReveladas() {
    UUID autorNaoRevelavel = UUID.randomUUID();
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(
            List.of(
                reviewBy(OTHER_USER, UNIT), // mesma unidade do ator: revelavel
                reviewBy(autorNaoRevelavel, OUTRA_UNIT))); // unidade diferente: nao revelavel

    service.list(RID, ACTOR, UNIT, false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<UUID>> authorIdsCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(userRepo).findAllById(authorIdsCaptor.capture());
    assertThat(authorIdsCaptor.getValue()).containsExactlyInAnyOrder(OTHER_USER);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<UUID>> unitIdsCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(unitRepo).findAllById(unitIdsCaptor.capture());
    assertThat(unitIdsCaptor.getValue()).containsExactlyInAnyOrder(UNIT);
  }

  @Test
  void hide_ocultaSemLiberarAVagaDoApartamento() {
    RecommendationReview r = reviewBy(OTHER_USER, UNIT);
    when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID)).thenReturn(Optional.of(r));
    // A lista PRECISA ser stubada com a avaliacao: sem isso ela volta vazia e o contador daria
    // zero mesmo que a recontagem nao acontecesse — o teste passava com o codigo errado.
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID)).thenReturn(List.of(r));

    service.hide(RID, REVIEW_ID);

    assertThat(r.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    verify(reviewRepo, never()).delete(any());
    // Recontou de verdade: a avaliacao existe na lista mas esta HIDDEN, entao nao conta.
    assertThat(rec.getReviewCount()).isZero();
    assertThat(rec.getRatingSum()).isZero();

    // E a VAGA do apartamento continua ocupada: a linha oculta ainda responde pela unidade, entao
    // um novo create da mesma UNIT tem que bater em REVIEW_UNIT_ALREADY_REVIEWED. É aqui que a
    // vaga mora (findByRecommendationIdAndUnitId), e nada exercitava isso.
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.of(r));
    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_UNIT_ALREADY_REVIEWED");
  }

  // ---- I3: teto do comentário no caminho interno (sem Bean Validation) --------------

  @Test
  void create_textoLongoDemais_lancaErroDeDominio() {
    // RecommendationService.create monta o ReviewRequest na mão: @Size(max=2000) nunca roda ali.
    // Sem o teto no dominio, um morador autenticado grava texto de tamanho arbitrario (a coluna
    // e `text`, sem limite, e nao ha CHECK de maximo).
    String longo = "a".repeat(2001);

    assertThatThrownBy(
            () -> service.create(RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, longo)))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_INVALID");
    verify(reviewRepo, never()).saveAndFlush(any());
  }

  @Test
  void create_textoNoLimiteExato_ehAceito() {
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());

    service.create(RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "a".repeat(2000)));

    verify(reviewRepo).saveAndFlush(any(RecommendationReview.class));
  }

  @Test
  void edit_textoLongoDemais_lancaErroDeDominio() {
    assertThatThrownBy(
            () ->
                service.edit(
                    RID, REVIEW_ID, ACTOR, false, req(ReviewSentiment.LIKE, 5, "a".repeat(2001))))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_INVALID");
    verify(reviewRepo, never()).save(any());
  }

  // ---- M6: unidade antes do corpo --------------------------------------------------

  @Test
  void create_semUnidadeEComCorpoInvalido_reclamaDaUnidadePrimeiro() {
    // A tela traduz o `code` numa explicacao. Se o corpo fosse validado antes, o sindico recebia
    // "escreva 10 caracteres" quando o problema real e que ele nunca podera avaliar.
    assertThatThrownBy(
            () -> service.create(RID, ACTOR, null, false, req(ReviewSentiment.LIKE, 9, "curto")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_REQUIRES_UNIT");
  }

  // ---- M9: corrida na cota vira erro de dominio, nao 500 ---------------------------

  @Test
  void create_corridaNaCota_traduzViolacaoDoIndiceUnico() {
    // Dois moradores do mesmo apartamento enviando ao mesmo tempo passam os dois pela checagem de
    // aplicacao; o segundo bate em ux_review_unit. Sem a traducao, o handler generico devolve 500.
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());
    when(reviewRepo.saveAndFlush(any(RecommendationReview.class)))
        .thenThrow(new DataIntegrityViolationException("ux_review_unit"));

    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("REVIEW_UNIT_ALREADY_REVIEWED");
  }

  // ---- I2: indicacao oculta esconde as avaliacoes dela ------------------------------

  private void recommendationHidden() {
    ReflectionTestUtils.setField(rec, "status", RecommendationStatus.HIDDEN);
  }

  @Test
  void list_deIndicacaoOculta_paraEstranho_naoEncontra() {
    recommendationHidden();
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    assertThatThrownBy(() -> service.list(RID, ACTOR, UNIT, false))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void list_deIndicacaoOculta_paraVisitanteSemSessao_naoEncontra() {
    recommendationHidden();

    assertThatThrownBy(() -> service.list(RID, null, null, false))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void list_deIndicacaoOculta_paraModerador_devolveAsAvaliacoes() {
    recommendationHidden();
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    assertThat(service.list(RID, ACTOR, OUTRA_UNIT, true)).hasSize(1);
  }

  @Test
  void list_deIndicacaoOculta_paraAutorDaIndicacao_devolveAsAvaliacoes() {
    recommendationHidden();
    ReflectionTestUtils.setField(rec, "recommendedByUserId", ACTOR);
    when(reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(RID))
        .thenReturn(List.of(reviewBy(OTHER_USER, UNIT)));

    assertThat(service.list(RID, ACTOR, UNIT, false)).hasSize(1);
  }

  @Test
  void create_emIndicacaoOculta_naoEncontra() {
    recommendationHidden();
    when(reviewRepo.findByRecommendationIdAndUnitId(RID, UNIT)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    RID, ACTOR, UNIT, false, req(ReviewSentiment.LIKE, 5, "Serviço muito bom.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
    verify(reviewRepo, never()).saveAndFlush(any());
  }

  @Test
  void edit_emIndicacaoOculta_naoEncontra() {
    recommendationHidden();
    when(reviewRepo.findByIdAndRecommendationId(REVIEW_ID, RID))
        .thenReturn(Optional.of(reviewBy(ACTOR, UNIT)));

    assertThatThrownBy(
            () ->
                service.edit(
                    RID,
                    REVIEW_ID,
                    ACTOR,
                    false,
                    req(ReviewSentiment.LIKE, 4, "Mudei de ideia depois.")))
        .isInstanceOf(RecommendationException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
    verify(reviewRepo, never()).save(any());
  }
}
