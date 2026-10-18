package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.complaint.dto.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.shared.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintAdminService {

  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final Set<ComplaintStatus> PENDING =
      EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.IN_REVIEW, ComplaintStatus.READY);

  private final ComplaintRepository repo;
  private final ComplaintCatalogService catalog;
  private final ComplaintEventWriter events;
  private final ComplaintEventRepository eventRepo;
  private final ComplaintViewAssembler views;
  private final UserRepository users;
  private final UnitRepository units;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Page<ComplaintAdminView> queue(Set<ComplaintStatus> statuses, int page, int size) {
    Set<ComplaintStatus> filter = statuses == null || statuses.isEmpty() ? PENDING : statuses;
    PageRequest pr =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "createdAt"));
    Page<Complaint> result = repo.findAll(ComplaintSpecs.statusIn(filter), pr);
    List<Complaint> content = result.getContent();
    List<ComplaintView> complaintViews = views.toViews(content, null);
    Map<UUID, User> authors = usersOf(content.stream().map(Complaint::getAuthorUserId));
    Map<UUID, Unit> unitIdx =
        units.findAllById(content.stream().map(Complaint::getAuthorUnitId).toList()).stream()
            .collect(Collectors.toMap(Unit::getId, Function.identity()));
    List<ComplaintAdminView> rows = new ArrayList<>();
    for (int i = 0; i < content.size(); i++) {
      Complaint c = content.get(i);
      rows.add(
          new ComplaintAdminView(
              complaintViews.get(i),
              nameOf(authors.get(c.getAuthorUserId())),
              codeOf(unitIdx.get(c.getAuthorUnitId()))));
    }
    return new PageImpl<>(rows, pr, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public ComplaintAdminDetailView detail(UUID id) {
    return detailOf(load(id));
  }

  @Transactional
  public ComplaintAdminDetailView startReview(UUID id, UUID actor) {
    return act(id, c -> c.startReview(actor));
  }

  @Transactional
  public ComplaintAdminDetailView markReady(UUID id, UUID actor) {
    return act(id, c -> c.markReady(actor));
  }

  @Transactional
  public ComplaintAdminDetailView reopenReview(UUID id, UUID actor) {
    return act(id, c -> c.reopenReview(actor));
  }

  @Transactional
  public ComplaintAdminDetailView reject(UUID id, UUID actor, String reason) {
    return act(id, c -> c.reject(actor, reason));
  }

  @Transactional
  public ComplaintAdminDetailView edit(UUID id, UUID actor, ComplaintFields f) {
    return act(
        id,
        c -> {
          catalog.requireUsable(f.typeId(), f.subtypeId(), f.locationId(), c.usedCatalogIds());
          c.editByAdmin(actor, f);
        });
  }

  @Transactional
  public ComplaintAdminDetailView setDueDate(UUID id, UUID actor, LocalDate due) {
    LocalDate today = LocalDate.ofInstant(clock.now(), ZONE);
    return act(id, c -> c.setDueDate(actor, due, today));
  }

  @Transactional
  public ComplaintAdminDetailView resolve(UUID id, UUID actor, String note) {
    return act(id, c -> c.resolve(actor, note, clock.now()));
  }

  private ComplaintAdminDetailView act(UUID id, Consumer<Complaint> action) {
    Complaint c = load(id);
    action.accept(c);
    events.flush(c);
    return detailOf(c);
  }

  private ComplaintAdminDetailView detailOf(Complaint c) {
    List<ComplaintEvent> history = eventRepo.findByComplaintIdOrderByCreatedAtAsc(c.getId());
    Map<UUID, User> people =
        usersOf(
            Stream.concat(
                Stream.of(c.getAuthorUserId()),
                history.stream().map(ComplaintEvent::getActorUserId)));
    String unitCode =
        units.findAllById(List.of(c.getAuthorUnitId())).stream()
            .findFirst()
            .map(Unit::getCode)
            .orElse(null);
    List<ComplaintEventView> eventViews =
        history.stream()
            .map(
                e ->
                    new ComplaintEventView(
                        e.getKind(),
                        nameOf(people.get(e.getActorUserId())),
                        e.getFromValue(),
                        e.getToValue(),
                        e.getCreatedAt()))
            .toList();
    return new ComplaintAdminDetailView(
        views.toView(c, null), nameOf(people.get(c.getAuthorUserId())), unitCode, eventViews);
  }

  private Map<UUID, User> usersOf(Stream<UUID> ids) {
    Set<UUID> set = ids.collect(Collectors.toSet());
    return users.findAllById(set).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private static String nameOf(User u) {
    return u == null ? null : u.getFullName();
  }

  private static String codeOf(Unit u) {
    return u == null ? null : u.getCode();
  }

  private Complaint load(UUID id) {
    return repo.findById(id).orElseThrow(ComplaintService::notFound);
  }
}
