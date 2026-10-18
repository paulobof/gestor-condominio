package br.com.condominio.feature.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.complaint.dto.ComplaintView;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.shared.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintAdminServiceTest {

  private ComplaintRepository repo;
  private ComplaintCatalogService catalog;
  private ComplaintEventWriter events;
  private ComplaintEventRepository eventRepo;
  private ComplaintViewAssembler views;
  private UserRepository users;
  private UnitRepository units;
  private Clock clock;
  private ComplaintAdminService service;

  private final UUID admin = UUID.randomUUID();
  private final UUID author = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(ComplaintRepository.class);
    catalog = mock(ComplaintCatalogService.class);
    events = mock(ComplaintEventWriter.class);
    eventRepo = mock(ComplaintEventRepository.class);
    views = mock(ComplaintViewAssembler.class);
    users = mock(UserRepository.class);
    units = mock(UnitRepository.class);
    clock = mock(Clock.class);
    service =
        new ComplaintAdminService(repo, catalog, events, eventRepo, views, users, units, clock);
    when(views.toView(any(), any())).thenReturn(mock(ComplaintView.class));
    when(views.toViews(any(), any())).thenReturn(List.of());
    when(users.findAllById(any())).thenReturn(List.of());
    when(units.findAllById(any())).thenReturn(List.of());
    when(eventRepo.findByComplaintIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
  }

  private Complaint persisted(ComplaintStatus status) {
    Complaint c =
        Complaint.create(
            author,
            UUID.randomUUID(),
            new ComplaintFields(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Lâmpada queimada na G1"));
    c.drainChanges();
    ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(c, "status", status);
    when(repo.findById(c.getId())).thenReturn(Optional.of(c));
    return c;
  }

  @Test
  @SuppressWarnings("unchecked")
  void queue_emptyStatuses_defaultsToPending() {
    when(repo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    service.queue(Set.of(), 0, 20);
    verify(repo).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void startReview_movesToInReviewAndFlushesEvents() {
    Complaint c = persisted(ComplaintStatus.OPEN);
    service.startReview(c.getId(), admin);
    assertThat(c.getStatus()).isEqualTo(ComplaintStatus.IN_REVIEW);
    verify(events).flush(c);
  }

  @Test
  void edit_grandfathersCurrentCatalogIds() {
    Complaint c = persisted(ComplaintStatus.IN_REVIEW);
    Set<UUID> current = c.usedCatalogIds();
    ComplaintFields sameIdsNewText =
        new ComplaintFields(
            c.getTypeId(), c.getSubtypeId(), c.getLocationId(), null, "Descrição padronizada");

    service.edit(c.getId(), admin, sameIdsNewText);

    verify(catalog).requireUsable(c.getTypeId(), c.getSubtypeId(), c.getLocationId(), current);
    assertThat(c.getDescription()).isEqualTo("Descrição padronizada");
  }

  @Test
  void edit_toInactiveItem_propagatesCatalogInvalid() {
    Complaint c = persisted(ComplaintStatus.IN_REVIEW);
    ComplaintFields f =
        new ComplaintFields(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Descrição nova ok");
    doThrow(new ComplaintException("CATALOG_INVALID", "x"))
        .when(catalog)
        .requireUsable(any(), any(), any(), any());
    assertThatThrownBy(() -> service.edit(c.getId(), admin, f))
        .extracting("code")
        .isEqualTo("CATALOG_INVALID");
  }

  @Test
  void setDueDate_usesSaoPauloToday() {
    // 02:30Z do dia 18 = 23:30 do dia 17 em São Paulo (Review Focus #5)
    when(clock.now()).thenReturn(Instant.parse("2026-10-18T02:30:00Z"));
    Complaint c = persisted(ComplaintStatus.FORWARDED);

    service.setDueDate(c.getId(), admin, LocalDate.of(2026, 10, 17));

    assertThat(c.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 17));
  }

  @Test
  void resolve_stampsResolvedAt() {
    Instant now = Instant.parse("2026-10-20T12:00:00Z");
    when(clock.now()).thenReturn(now);
    Complaint c = persisted(ComplaintStatus.FORWARDED);
    service.resolve(c.getId(), admin, "Pintado");
    assertThat(c.getResolvedAt()).isEqualTo(now);
  }

  @Test
  void reject_unknownComplaint_isNotFound() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reject(id, admin, "motivo"))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }
}
