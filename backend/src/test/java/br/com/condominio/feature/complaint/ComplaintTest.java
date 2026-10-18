package br.com.condominio.feature.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintTest {

  private final UUID author = UUID.randomUUID();
  private final UUID admin = UUID.randomUUID();
  private final UUID unit = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-17T15:00:00Z");
  private final LocalDate today = LocalDate.of(2026, 10, 17);

  private ComplaintFields fields(String description) {
    return new ComplaintFields(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hall do 5º", description);
  }

  private Complaint open() {
    Complaint c = Complaint.create(author, unit, fields("Parede descascando no hall"));
    c.drainChanges();
    return c;
  }

  private Complaint inStatus(ComplaintStatus s) {
    Complaint c = open();
    ReflectionTestUtils.setField(c, "status", s);
    return c;
  }

  @Test
  void create_startsOpen_trimsAndRecordsCreated() {
    Complaint c = Complaint.create(author, unit, fields("  Parede descascando no hall  "));
    assertThat(c.getStatus()).isEqualTo(ComplaintStatus.OPEN);
    assertThat(c.getDescription()).isEqualTo("Parede descascando no hall");
    assertThat(c.drainChanges())
        .singleElement()
        .extracting(ComplaintChange::kind)
        .isEqualTo(ComplaintEventKind.CREATED);
  }

  @Test
  void create_blankOrShortDescription_isInvalidInput() {
    assertThatThrownBy(() -> Complaint.create(author, unit, fields("          ")))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
    assertThatThrownBy(() -> Complaint.create(author, unit, fields("curto")))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  void create_tooLongDescriptionOrDetail_isInvalidInput() {
    assertThatThrownBy(() -> Complaint.create(author, unit, fields("x".repeat(2001))))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
    ComplaintFields longDetail =
        new ComplaintFields(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "y".repeat(201),
            "descrição ok");
    assertThatThrownBy(() -> Complaint.create(author, unit, longDetail))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  void create_blankDetail_becomesNull() {
    ComplaintFields f =
        new ComplaintFields(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "   ", "descrição ok aqui");
    assertThat(Complaint.create(author, unit, f).getLocationDetail()).isNull();
  }

  @Test
  void editByAuthor_onlyAuthorAndOnlyOpen() {
    Complaint c = open();
    c.editByAuthor(author, fields("Nova descrição do problema"));
    assertThat(c.getDescription()).isEqualTo("Nova descrição do problema");
    assertThat(c.drainChanges())
        .extracting(ComplaintChange::kind)
        .containsExactly(ComplaintEventKind.EDITED);

    assertThatThrownBy(() -> c.editByAuthor(UUID.randomUUID(), fields("outra descrição aqui")))
        .extracting("code")
        .isEqualTo("NOT_FOUND");

    Complaint reviewing = inStatus(ComplaintStatus.IN_REVIEW);
    assertThatThrownBy(() -> reviewing.editByAuthor(author, fields("outra descrição aqui")))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void cancelByAuthor_onlyWhenOpen() {
    open().cancelByAuthor(author);
    assertThatThrownBy(() -> inStatus(ComplaintStatus.READY).cancelByAuthor(author))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void happyPath_openToResolved_recordsStatusChanges() {
    Complaint c = open();
    UUID batch = UUID.randomUUID();
    c.startReview(admin);
    c.markReady(admin);
    c.forward(admin, batch, now);
    c.setDueDate(admin, today.plusDays(10), today);
    c.resolve(admin, "Pintado", now.plusSeconds(60));

    assertThat(c.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
    assertThat(c.getBatchId()).isEqualTo(batch);
    assertThat(c.getForwardedAt()).isEqualTo(now);
    assertThat(c.getResolutionNote()).isEqualTo("Pintado");
    assertThat(c.drainChanges())
        .extracting(ComplaintChange::kind)
        .containsExactly(
            ComplaintEventKind.STATUS_CHANGED,
            ComplaintEventKind.STATUS_CHANGED,
            ComplaintEventKind.STATUS_CHANGED,
            ComplaintEventKind.DUE_DATE_SET,
            ComplaintEventKind.STATUS_CHANGED);
  }

  @Test
  void reopenReview_fromReady() {
    Complaint c = inStatus(ComplaintStatus.READY);
    c.reopenReview(admin);
    assertThat(c.getStatus()).isEqualTo(ComplaintStatus.IN_REVIEW);
  }

  @ParameterizedTest
  @EnumSource(
      value = ComplaintStatus.class,
      names = {"OPEN", "IN_REVIEW", "READY"})
  void reject_allowedBeforeForward(ComplaintStatus from) {
    Complaint c = inStatus(from);
    c.reject(admin, "  Duplicado do #12  ");
    assertThat(c.getStatus()).isEqualTo(ComplaintStatus.REJECTED);
    assertThat(c.getRejectionReason()).isEqualTo("Duplicado do #12");
  }

  @ParameterizedTest
  @EnumSource(
      value = ComplaintStatus.class,
      names = {"FORWARDED", "RESOLVED", "REJECTED"})
  void reject_notAllowedAfterForward(ComplaintStatus from) {
    assertThatThrownBy(() -> inStatus(from).reject(admin, "motivo"))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void reject_blankReason_isInvalidInput() {
    assertThatThrownBy(() -> open().reject(admin, "   "))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  void forward_onlyFromReady() {
    assertThatThrownBy(
            () -> inStatus(ComplaintStatus.IN_REVIEW).forward(admin, UUID.randomUUID(), now))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void editByAdmin_onlyInReviewOrReady() {
    inStatus(ComplaintStatus.IN_REVIEW).editByAdmin(admin, fields("ajustada pelo admin"));
    inStatus(ComplaintStatus.READY).editByAdmin(admin, fields("ajustada pelo admin"));
    assertThatThrownBy(() -> open().editByAdmin(admin, fields("ajustada pelo admin")))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void setDueDate_todayAcceptedYesterdayRejected_onlyWhenForwarded() {
    Complaint c = inStatus(ComplaintStatus.FORWARDED);
    c.setDueDate(admin, today, today); // Review Focus #5
    assertThat(c.getDueDate()).isEqualTo(today);
    assertThatThrownBy(() -> c.setDueDate(admin, today.minusDays(1), today))
        .extracting("code")
        .isEqualTo("DUE_DATE_PAST");
    assertThatThrownBy(() -> inStatus(ComplaintStatus.READY).setDueDate(admin, today, today))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void setDueDate_recordsOldAndNewValue() {
    Complaint c = inStatus(ComplaintStatus.FORWARDED);
    c.setDueDate(admin, today.plusDays(1), today);
    c.drainChanges();
    c.setDueDate(admin, today.plusDays(5), today);
    ComplaintChange ch = c.drainChanges().get(0);
    assertThat(ch.fromValue()).isEqualTo(today.plusDays(1).toString());
    assertThat(ch.toValue()).isEqualTo(today.plusDays(5).toString());
  }

  @Test
  void visibility_authorManagerOrBoard() {
    Complaint c = open();
    UUID stranger = UUID.randomUUID();
    assertThat(c.isVisibleTo(author, false)).isTrue();
    assertThat(c.isVisibleTo(stranger, true)).isTrue();
    assertThat(c.isVisibleTo(stranger, false)).isFalse();
    assertThat(inStatus(ComplaintStatus.FORWARDED).isVisibleTo(stranger, false)).isTrue();
    assertThat(inStatus(ComplaintStatus.RESOLVED).isVisibleTo(stranger, false)).isTrue();
    assertThat(inStatus(ComplaintStatus.REJECTED).isVisibleTo(stranger, false)).isFalse();
  }

  @Test
  void requirePhotoEditableBy_authorAndOpen() {
    open().requirePhotoEditableBy(author);
    assertThatThrownBy(() -> open().requirePhotoEditableBy(UUID.randomUUID()))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
    assertThatThrownBy(() -> inStatus(ComplaintStatus.IN_REVIEW).requirePhotoEditableBy(author))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void syndicResponse_setEditClear_onlyAfterForward() {
    Complaint c = inStatus(ComplaintStatus.FORWARDED);
    c.updateSyndicResponse(admin, "  Orçamento aprovado, pintor vem dia 30.  ", now);
    assertThat(c.getSyndicResponse()).isEqualTo("Orçamento aprovado, pintor vem dia 30.");
    assertThat(c.getSyndicResponseAt()).isEqualTo(now);
    assertThat(c.drainChanges())
        .singleElement()
        .satisfies(
            ch -> {
              assertThat(ch.kind()).isEqualTo(ComplaintEventKind.RESPONSE_SET);
              assertThat(ch.toValue()).isNull(); // conteúdo não vai para o log
            });

    c.updateSyndicResponse(admin, "Orçamento aprovado, pintor vem dia 30.", now.plusSeconds(5));
    assertThat(c.drainChanges()).isEmpty(); // igual ao atual: nada muda
    assertThat(c.getSyndicResponseAt()).isEqualTo(now);

    c.updateSyndicResponse(admin, "   ", now.plusSeconds(10));
    assertThat(c.getSyndicResponse()).isNull();
    assertThat(c.getSyndicResponseAt()).isNull();

    Complaint resolved = inStatus(ComplaintStatus.RESOLVED);
    resolved.updateSyndicResponse(admin, "Concluído.", now);
    assertThat(resolved.getSyndicResponse()).isEqualTo("Concluído.");
  }

  @Test
  void syndicResponse_beforeForward_isInvalidState() {
    assertThatThrownBy(() -> inStatus(ComplaintStatus.READY).updateSyndicResponse(admin, "x", now))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
  }

  @Test
  void syndicResponse_tooLong_isInvalidInput() {
    assertThatThrownBy(
            () ->
                inStatus(ComplaintStatus.FORWARDED)
                    .updateSyndicResponse(admin, "x".repeat(2001), now))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
  }
}
