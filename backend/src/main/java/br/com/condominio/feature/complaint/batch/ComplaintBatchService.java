package br.com.condominio.feature.complaint.batch;

import br.com.condominio.feature.complaint.*;
import br.com.condominio.feature.complaint.batch.dto.BatchPreview;
import br.com.condominio.feature.complaint.batch.dto.ComplaintBatchView;
import br.com.condominio.feature.complaint.catalog.CatalogIndex;
import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.shared.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintBatchService {

  private final ComplaintRepository repo;
  private final ComplaintBatchRepository batches;
  private final ComplaintCatalogService catalog;
  private final ComplaintEventWriter events;
  private final UserRepository users;
  private final Clock clock;
  private final ComplaintBatchTextRenderer renderer;

  @Transactional(readOnly = true)
  public BatchPreview preview(List<UUID> ids) {
    List<Complaint> list = loadReady(ids);
    return new BatchPreview(render(list, today(clock.now())), list.size());
  }

  /**
   * Tudo ou nada: valida todos antes de mexer em qualquer um. Se outro admin alterou um chamado
   * entre o preview e a confirmação, ou ele já não está READY (409), ou o @Version estoura no
   * commit (409 CONCURRENT_MODIFICATION). Nos dois casos a transação inteira volta.
   *
   * <p>{@code expectedText} é o texto que o admin copiou para a síndica; se algum chamado foi
   * editado depois do preview, o texto gravado divergiria do enviado — então recusa (409).
   */
  @Transactional
  public ComplaintBatchView create(List<UUID> ids, String expectedText, UUID actor) {
    List<Complaint> list = loadReady(ids);
    Instant now = clock.now();
    String text = render(list, today(now));
    if (expectedText != null && !expectedText.equals(text)) {
      throw new ComplaintException(
          "BATCH_STALE",
          "Alguma reclamação mudou depois que o texto foi gerado. Gere o texto de novo e copie antes"
              + " de confirmar.");
    }
    ComplaintBatch batch = batches.save(ComplaintBatch.create(actor, now, text, list.size()));
    for (Complaint c : list) {
      c.forward(actor, batch.getId(), now);
      events.flush(c);
    }
    return view(batch, namesOf(Set.of(actor)));
  }

  @Transactional(readOnly = true)
  public List<ComplaintBatchView> list() {
    List<ComplaintBatch> all = batches.findAllByOrderBySentAtDesc();
    Map<UUID, String> names =
        namesOf(all.stream().map(ComplaintBatch::getSentByUserId).collect(Collectors.toSet()));
    return all.stream().map(b -> view(b, names)).toList();
  }

  private List<Complaint> loadReady(List<UUID> ids) {
    Set<UUID> distinct = new LinkedHashSet<>(ids);
    List<Complaint> list = repo.findAllById(distinct);
    if (list.size() != distinct.size()) {
      throw new ComplaintException("NOT_FOUND", "Alguma reclamação do lote não foi encontrada.");
    }
    for (Complaint c : list) {
      if (c.getStatus() != ComplaintStatus.READY) {
        throw new ComplaintException(
            "INVALID_STATE",
            "A reclamação #" + c.getCode() + " não está mais pronta para encaminhar. Recarregue.");
      }
    }
    return list;
  }

  private String render(List<Complaint> list, LocalDate date) {
    CatalogIndex idx =
        catalog.index(
            list.stream().map(Complaint::getTypeId).collect(Collectors.toSet()),
            list.stream().map(Complaint::getSubtypeId).collect(Collectors.toSet()),
            list.stream().map(Complaint::getLocationId).collect(Collectors.toSet()));
    List<BatchLine> lines =
        list.stream()
            .map(
                c ->
                    new BatchLine(
                        c.getCode() == null ? 0 : c.getCode(),
                        idx.typeName(c.getTypeId()),
                        idx.typeOrder(c.getTypeId()),
                        idx.subtypeName(c.getSubtypeId()),
                        idx.locationName(c.getLocationId()),
                        c.getLocationDetail(),
                        c.getDescription()))
            .toList();
    return renderer.render(date, lines);
  }

  private static LocalDate today(Instant now) {
    return LocalDate.ofInstant(now, ComplaintAdminService.ZONE);
  }

  private Map<UUID, String> namesOf(Set<UUID> ids) {
    return users.findAllById(ids).stream()
        .collect(Collectors.toMap(User::getId, User::getFullName));
  }

  private static ComplaintBatchView view(ComplaintBatch b, Map<UUID, String> names) {
    return new ComplaintBatchView(
        b.getId(),
        b.getSentAt(),
        names.get(b.getSentByUserId()),
        b.getComplaintCount(),
        b.getRenderedText());
  }
}
