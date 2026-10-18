package br.com.condominio.feature.complaint.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.complaint.*;
import br.com.condominio.feature.complaint.batch.dto.BatchPreview;
import br.com.condominio.feature.complaint.batch.dto.ComplaintBatchView;
import br.com.condominio.feature.complaint.catalog.CatalogIndex;
import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.shared.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintBatchServiceTest {

  private ComplaintRepository repo;
  private ComplaintBatchRepository batches;
  private ComplaintCatalogService catalog;
  private ComplaintEventWriter events;
  private UserRepository users;
  private Clock clock;
  private ComplaintBatchService service;

  private final UUID admin = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-17T15:00:00Z");

  @BeforeEach
  void setUp() {
    repo = mock(ComplaintRepository.class);
    batches = mock(ComplaintBatchRepository.class);
    catalog = mock(ComplaintCatalogService.class);
    events = mock(ComplaintEventWriter.class);
    users = mock(UserRepository.class);
    clock = mock(Clock.class);
    service =
        new ComplaintBatchService(
            repo, batches, catalog, events, users, clock, new ComplaintBatchTextRenderer());
    when(clock.now()).thenReturn(now);
    when(catalog.index(any(), any(), any()))
        .thenReturn(new CatalogIndex(Map.of(), Map.of(), Map.of()));
    when(users.findAllById(any())).thenReturn(List.of());
    when(batches.save(any()))
        .thenAnswer(
            i -> {
              ComplaintBatch b = i.getArgument(0);
              ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
              return b;
            });
  }

  private Complaint complaint(long code, ComplaintStatus status) {
    Complaint c =
        Complaint.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ComplaintFields(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Descrição da reclamação " + code));
    c.drainChanges();
    ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(c, "code", code);
    ReflectionTestUtils.setField(c, "status", status);
    return c;
  }

  @Test
  void preview_rendersWithoutChangingAnything() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    when(repo.findAllById(any())).thenReturn(List.of(a));

    BatchPreview p = service.preview(List.of(a.getId()));

    assertThat(p.count()).isEqualTo(1);
    assertThat(p.text()).contains("#1").contains("Lote de 17/10/2026");
    assertThat(a.getStatus()).isEqualTo(ComplaintStatus.READY);
    verifyNoInteractions(batches, events);
  }

  @Test
  void create_forwardsAllAndSavesBatch() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    Complaint b = complaint(2, ComplaintStatus.READY);
    when(repo.findAllById(any())).thenReturn(List.of(a, b));

    ComplaintBatchView view = service.create(List.of(a.getId(), b.getId(), a.getId()), null, admin);

    assertThat(view.complaintCount()).isEqualTo(2);
    assertThat(a.getStatus()).isEqualTo(ComplaintStatus.FORWARDED);
    assertThat(b.getBatchId()).isEqualTo(view.id());
    assertThat(b.getForwardedAt()).isEqualTo(now);
    verify(events).flush(a);
    verify(events).flush(b);
  }

  @Test
  void create_oneNoLongerReady_conflictsAndChangesNothing() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    Complaint rejected = complaint(2, ComplaintStatus.REJECTED);
    when(repo.findAllById(any())).thenReturn(List.of(a, rejected));

    assertThatThrownBy(() -> service.create(List.of(a.getId(), rejected.getId()), null, admin))
        .extracting("code")
        .isEqualTo("INVALID_STATE");
    assertThat(a.getStatus()).isEqualTo(ComplaintStatus.READY);
    verifyNoInteractions(batches, events);
  }

  @Test
  void create_unknownId_isNotFound() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    when(repo.findAllById(any())).thenReturn(List.of(a));
    assertThatThrownBy(() -> service.create(List.of(a.getId(), UUID.randomUUID()), null, admin))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void create_expectedTextMatchesPreview_forwards() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    when(repo.findAllById(any())).thenReturn(List.of(a));
    String shown = service.preview(List.of(a.getId())).text();

    ComplaintBatchView view = service.create(List.of(a.getId()), shown, admin);

    assertThat(view.renderedText()).isEqualTo(shown);
    assertThat(a.getStatus()).isEqualTo(ComplaintStatus.FORWARDED);
  }

  @Test
  void create_textChangedSincePreview_isStaleAndChangesNothing() {
    Complaint a = complaint(1, ComplaintStatus.READY);
    when(repo.findAllById(any())).thenReturn(List.of(a));
    String shown = service.preview(List.of(a.getId())).text();
    ReflectionTestUtils.setField(a, "description", "Descrição editada pelo admin");

    assertThatThrownBy(() -> service.create(List.of(a.getId()), shown, admin))
        .isInstanceOf(ComplaintException.class)
        .extracting("code")
        .isEqualTo("BATCH_STALE");
    assertThat(a.getStatus()).isEqualTo(ComplaintStatus.READY);
    verifyNoInteractions(batches, events);
  }
}
