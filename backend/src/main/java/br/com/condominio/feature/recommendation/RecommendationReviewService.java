package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.ReviewRequest;
import br.com.condominio.feature.recommendation.dto.ReviewView;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Avaliação de indicações: uma por (indicação, apartamento). Cria, edita e apaga a avaliação do
 * próprio apartamento, lista com anonimato entre vizinhos e modera (oculta), mantendo os contadores
 * desnormalizados de {@link Recommendation} sincronizados.
 */
@Service
@RequiredArgsConstructor
public class RecommendationReviewService {

  private final RecommendationRepository repo;
  private final RecommendationReviewRepository reviewRepo;
  private final UserRepository userRepo;
  private final UnitRepository unitRepo;

  @Transactional
  public ReviewView create(
      UUID recommendationId,
      UUID actorId,
      UUID actorUnitId,
      boolean canModerate,
      ReviewRequest req) {
    // Unidade ANTES do corpo: quem não tem unidade não pode avaliar de jeito nenhum, e mandar
    // REVIEW_INVALID nesse caso faria a tela explicar "escreva 10 caracteres" a um síndico que
    // nunca poderia avaliar. Ordem definida no plano.
    if (actorUnitId == null) {
      throw new RecommendationException(
          "REVIEW_REQUIRES_UNIT", "Só moradores com unidade podem avaliar indicações.");
    }
    validate(req);
    Recommendation recommendation = loadVisible(recommendationId, actorId, canModerate);
    reviewRepo
        .findByRecommendationIdAndUnitId(recommendationId, actorUnitId)
        .ifPresent(
            existing -> {
              throw new RecommendationException(
                  "REVIEW_UNIT_ALREADY_REVIEWED", "Este apartamento já avaliou esta indicação.");
            });
    RecommendationReview review =
        RecommendationReview.create(
            recommendationId,
            actorUnitId,
            actorId,
            req.sentiment(),
            req.rating().shortValue(),
            req.text());
    try {
      reviewRepo.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      // Corrida: dois moradores do mesmo apartamento enviando ao mesmo tempo passam os dois pela
      // checagem acima e o segundo bate no índice único ux_review_unit. Traduz para o erro de
      // domínio (spec §5.2: "a violação da unique é traduzida em erro de domínio, não em 500") em
      // vez de deixar cair no handler genérico. Catch LOCAL de propósito: um handler global de
      // DataIntegrityViolationException cobriria toda unique do sistema com esta mensagem.
      throw new RecommendationException(
          "REVIEW_UNIT_ALREADY_REVIEWED", "Este apartamento já avaliou esta indicação.");
    }
    recountAndSave(recommendation);
    return toViews(List.of(review), actorId, actorUnitId, false).get(0);
  }

  @Transactional
  public ReviewView edit(
      UUID recommendationId, UUID reviewId, UUID actorId, boolean canModerate, ReviewRequest req) {
    validate(req);
    loadVisible(recommendationId, actorId, canModerate);
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    review.edit(req.sentiment(), req.rating().shortValue(), req.text());
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
    return toViews(List.of(review), actorId, review.getUnitId(), false).get(0);
  }

  @Transactional
  public void delete(UUID recommendationId, UUID reviewId, UUID actorId) {
    RecommendationReview review = loadOwned(recommendationId, reviewId, actorId);
    reviewRepo.delete(review);
    recountAndSave(load(recommendationId));
  }

  /**
   * Lista as avaliações da indicação. Sem moderação, avaliações ocultas somem da lista. O nome do
   * autor e o código da unidade só aparecem para quem modera ou mora na mesma unidade da avaliação
   * — para todo o resto, {@code authorName}/{@code unitCode} vêm nulos.
   *
   * <p>Indicação oculta esconde também as avaliações dela: mesma regra do detalhe.
   */
  @Transactional
  public List<ReviewView> list(
      UUID recommendationId, UUID actorId, UUID actorUnitId, boolean canModerate) {
    loadVisible(recommendationId, actorId, canModerate);
    List<RecommendationReview> reviews =
        reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(recommendationId);
    List<RecommendationReview> visible =
        canModerate
            ? reviews
            : reviews.stream().filter(r -> r.getStatus() == ReviewStatus.ACTIVE).toList();
    return toViews(visible, actorId, actorUnitId, canModerate);
  }

  /** Oculta a avaliação (moderação). Nunca apaga — recontabiliza para tirá-la das médias. */
  @Transactional
  public void hide(UUID recommendationId, UUID reviewId) {
    RecommendationReview review =
        reviewRepo
            .findByIdAndRecommendationId(reviewId, recommendationId)
            .orElseThrow(
                () -> new RecommendationException("NOT_FOUND", "Avaliação não encontrada."));
    review.hide();
    reviewRepo.save(review);
    recountAndSave(load(recommendationId));
  }

  /** Mesmo teto do {@code @Size(max=2000)} de {@link ReviewRequest#text()}. */
  static final int MAX_TEXT_LENGTH = 2000;

  // ReviewRequest#text já chega stripado (compact constructor do record) quando a chamada vem da
  // fronteira HTTP com @Valid — mas ReviewRequest também é montado programaticamente em
  // RecommendationService.create (indicação + primeira avaliação), onde as anotações de Bean
  // Validation nunca são acionadas. Sem esta checagem de domínio, um texto curto ou longo demais,
  // ou uma nota fora de 1..5, vindos desse caminho passariam direto: o mínimo e a nota só seriam
  // barrados pela CHECK do banco (chk_review_rating / chk_review_text), virando 500 em vez de 400,
  // e o máximo não seria barrado por ninguém — a coluna é `text`, sem limite — deixando um morador
  // autenticado gravar comentário de tamanho arbitrário.
  private void validate(ReviewRequest req) {
    if (req.sentiment() == null
        || req.rating() == null
        || req.rating() < 1
        || req.rating() > 5
        || req.text() == null
        || req.text().length() < 10
        || req.text().length() > MAX_TEXT_LENGTH) {
      throw new RecommendationException(
          "REVIEW_INVALID",
          "Avaliação precisa de sentimento, nota entre 1 e 5 e comentário de 10 a "
              + MAX_TEXT_LENGTH
              + " caracteres.");
    }
  }

  private List<ReviewView> toViews(
      List<RecommendationReview> reviews, UUID actorId, UUID actorUnitId, boolean canModerate) {
    // PII (nome, unidade) só é buscada para quem tem direito de vê-la: escopa o lote às
    // avaliações reveladas, nunca a todas — senão carrega nome/unidade de todo mundo à toa.
    List<RecommendationReview> revealable =
        reviews.stream().filter(r -> reveals(r, actorUnitId, canModerate)).toList();
    Set<UUID> authorIds =
        revealable.stream().map(RecommendationReview::getAuthorUserId).collect(Collectors.toSet());
    Map<UUID, String> names =
        authorIds.isEmpty()
            ? Map.of()
            : userRepo.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    Set<UUID> unitIds =
        revealable.stream().map(RecommendationReview::getUnitId).collect(Collectors.toSet());
    Map<UUID, String> codes =
        unitIds.isEmpty()
            ? Map.of()
            : unitRepo.findAllById(unitIds).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getCode));
    return reviews.stream()
        .map(r -> toView(r, actorId, actorUnitId, canModerate, names, codes))
        .toList();
  }

  private boolean reveals(RecommendationReview r, UUID actorUnitId, boolean canModerate) {
    return canModerate || sameUnit(r, actorUnitId);
  }

  /**
   * Só a metade "mesma unidade" de {@link #reveals}, sem o motivo "modera". É o que o frontend usa
   * pra saber se o PRÓPRIO apartamento já avaliou — nunca confundir com {@code reveals}, que também
   * é true pra quem modera avaliação de qualquer unidade.
   */
  private boolean sameUnit(RecommendationReview r, UUID actorUnitId) {
    return actorUnitId != null && actorUnitId.equals(r.getUnitId());
  }

  private ReviewView toView(
      RecommendationReview r,
      UUID actorId,
      UUID actorUnitId,
      boolean canModerate,
      Map<UUID, String> names,
      Map<UUID, String> codes) {
    boolean mine = r.getAuthorUserId().equals(actorId);
    boolean reveal = reveals(r, actorUnitId, canModerate);
    return new ReviewView(
        r.getId(),
        r.getSentiment().name(),
        r.getRating(),
        r.getText(),
        r.getCreatedAt(),
        mine,
        reveal ? names.get(r.getAuthorUserId()) : null,
        reveal ? codes.get(r.getUnitId()) : null,
        sameUnit(r, actorUnitId));
  }

  /** Recalcula os quatro contadores a partir das avaliações ATIVAS. Oculta não conta. */
  void recountAndSave(Recommendation r) {
    List<RecommendationReview> active =
        reviewRepo.findByRecommendationIdOrderByCreatedAtDesc(r.getId()).stream()
            .filter(rev -> rev.getStatus() == ReviewStatus.ACTIVE)
            .toList();
    int likes =
        (int) active.stream().filter(rev -> rev.getSentiment() == ReviewSentiment.LIKE).count();
    int ratingSum = active.stream().mapToInt(RecommendationReview::getRating).sum();
    r.updateReviewCounts(likes, active.size() - likes, active.size(), ratingSum);
    repo.save(r);
  }

  private Recommendation load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Indicação não encontrada."));
  }

  /**
   * Carrega a indicação aplicando a MESMA regra de visibilidade do detalhe ({@link
   * Recommendation#isVisibleTo}). Ocultar a indicação tem que ocultar as avaliações dela e impedir
   * novas — senão a moderação tira do ar só a casca.
   */
  private Recommendation loadVisible(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = load(id);
    if (!r.isVisibleTo(actorId, canModerate)) {
      throw new RecommendationException("NOT_FOUND", "Indicação não encontrada.");
    }
    return r;
  }

  private RecommendationReview loadOwned(UUID recommendationId, UUID reviewId, UUID actorId) {
    RecommendationReview review =
        reviewRepo
            .findByIdAndRecommendationId(reviewId, recommendationId)
            .orElseThrow(
                () -> new RecommendationException("NOT_FOUND", "Avaliação não encontrada."));
    if (!review.getAuthorUserId().equals(actorId)) {
      throw new RecommendationException("FORBIDDEN", "Sem permissão sobre esta avaliação.");
    }
    return review;
  }
}
