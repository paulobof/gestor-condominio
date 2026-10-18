package br.com.condominio.feature.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintServiceTest {

  private ComplaintRepository repo;
  private ComplaintCatalogService catalog;
  private ComplaintEventWriter events;
  private ComplaintViewAssembler views;
  private ComplaintService service;

  private final UUID author = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();
  private final UUID unit = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(ComplaintRepository.class);
    catalog = mock(ComplaintCatalogService.class);
    events = mock(ComplaintEventWriter.class);
    views = mock(ComplaintViewAssembler.class);
    service = new ComplaintService(repo, catalog, events, views);
    // simula o UUID que o Hibernate atribui no persist
    when(repo.saveAndFlush(any(Complaint.class)))
        .thenAnswer(
            i -> {
              Complaint c = i.getArgument(0);
              ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
              return c;
            });
  }

  private ComplaintFields fields() {
    return new ComplaintFields(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Lâmpada queimada na G1");
  }

  private Complaint persisted(ComplaintStatus status) {
    Complaint c = Complaint.create(author, unit, fields());
    c.drainChanges();
    ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(c, "status", status);
    when(repo.findById(c.getId())).thenReturn(Optional.of(c));
    return c;
  }

  @Test
  void create_withoutUnit_isNoUnit() {
    assertThatThrownBy(() -> service.create(author, null, fields()))
        .extracting("code")
        .isEqualTo("NO_UNIT");
    verifyNoInteractions(repo);
  }

  @Test
  void create_validatesCatalogSavesAndFlushesEvents() {
    ComplaintFields f = fields();
    service.create(author, unit, f);

    verify(catalog).requireUsable(f.typeId(), f.subtypeId(), f.locationId(), Set.of());
    verify(repo).saveAndFlush(any(Complaint.class));
    verify(events).flush(any(Complaint.class));
    verify(views).toView(any(Complaint.class), eq(author));
  }

  @Test
  void get_strangerNotOnBoard_isNotFound() {
    Complaint c = persisted(ComplaintStatus.IN_REVIEW);
    assertThatThrownBy(() -> service.get(c.getId(), stranger, unit, false))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void get_strangerWithUnitOnBoard_isVisible() {
    Complaint c = persisted(ComplaintStatus.FORWARDED);
    service.get(c.getId(), stranger, unit, false);
    verify(views).toView(c, stranger);
  }

  @Test
  void get_strangerWithoutUnitOnBoard_isNoUnit() {
    Complaint c = persisted(ComplaintStatus.RESOLVED);
    assertThatThrownBy(() -> service.get(c.getId(), stranger, null, false))
        .extracting("code")
        .isEqualTo("NO_UNIT");
    verifyNoInteractions(views);
  }

  @Test
  void get_withoutUnit_unknownId_isNoUnit_soExistenceIsNotRevealed() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(id, stranger, null, false))
        .extracting("code")
        .isEqualTo("NO_UNIT");
  }

  @Test
  void get_authorWithoutUnit_seesOwnComplaint() {
    Complaint c = persisted(ComplaintStatus.IN_REVIEW);
    service.get(c.getId(), author, null, false);
    verify(views).toView(c, author);
  }

  @Test
  void get_managerWithoutUnit_seesAnyComplaint() {
    Complaint c = persisted(ComplaintStatus.OPEN);
    service.get(c.getId(), stranger, null, true);
    verify(views).toView(c, stranger);
  }

  @Test
  void get_unknownId_isNotFound() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(id, author, unit, false))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void update_byStranger_isNotFound_withoutTouchingCatalog() {
    Complaint c = persisted(ComplaintStatus.OPEN);
    assertThatThrownBy(() -> service.update(c.getId(), stranger, fields()))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
    verifyNoInteractions(catalog);
  }

  @Test
  void update_byAuthor_grandfathersCurrentCatalogIds() {
    Complaint c = persisted(ComplaintStatus.OPEN);
    Set<UUID> current = c.usedCatalogIds();
    ComplaintFields f = fields();

    service.update(c.getId(), author, f);

    verify(catalog).requireUsable(f.typeId(), f.subtypeId(), f.locationId(), current);
    verify(events).flush(c);
    assertThat(c.getTypeId()).isEqualTo(f.typeId());
  }

  @Test
  void cancel_softDeletesOpenComplaint() {
    Complaint c = persisted(ComplaintStatus.OPEN);
    service.cancel(c.getId(), author);
    verify(events).flush(c);
    verify(repo).delete(c);
  }

  @Test
  void mine_listsAuthorComplaints() {
    Complaint c = persisted(ComplaintStatus.REJECTED);
    when(repo.findByAuthorUserIdOrderByCreatedAtDesc(author)).thenReturn(List.of(c));
    service.mine(author);
    verify(views).toViews(List.of(c), author);
  }

  @Test
  void board_withoutUnitAndWithoutManage_isNoUnit() {
    assertThatThrownBy(() -> service.board(author, null, false, null, null, null, 0, 20))
        .extracting("code")
        .isEqualTo("NO_UNIT");
  }

  @Test
  @SuppressWarnings("unchecked")
  void board_managerWithoutUnit_isAllowed() {
    when(repo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    service.board(author, null, true, null, null, null, 0, 20);
    verify(repo).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void board_statusOutsideBoard_isInvalidInput() {
    assertThatThrownBy(
            () -> service.board(author, unit, false, null, null, ComplaintStatus.OPEN, 0, 20))
        .extracting("code")
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  @SuppressWarnings("unchecked")
  void board_sortsForwardedBeforeResolved_thenMostRecent() {
    when(repo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    service.board(author, unit, false, null, null, null, 2, 500);

    verify(repo).findAll(any(Specification.class), pageable.capture());
    Pageable p = pageable.getValue();
    assertThat(p.getPageNumber()).isEqualTo(2);
    assertThat(p.getPageSize()).isEqualTo(100);
    assertThat(p.getSort().getOrderFor("status").getDirection()).isEqualTo(Sort.Direction.ASC);
    assertThat(p.getSort().getOrderFor("forwardedAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(p.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
  }
}
