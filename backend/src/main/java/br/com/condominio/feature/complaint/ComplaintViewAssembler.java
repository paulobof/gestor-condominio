package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.catalog.CatalogIndex;
import br.com.condominio.feature.complaint.catalog.ComplaintCatalogService;
import br.com.condominio.feature.complaint.dto.ComplaintPhotoView;
import br.com.condominio.feature.complaint.dto.ComplaintView;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Monta {@link ComplaintView} em lote: 1 query de catálogo e 1 de fotos por página. */
@Component
@RequiredArgsConstructor
public class ComplaintViewAssembler {

  private final ComplaintCatalogService catalog;
  private final ComplaintPhotoRepository photos;

  public ComplaintView toView(Complaint c, UUID viewerId) {
    return toViews(List.of(c), viewerId).get(0);
  }

  public List<ComplaintView> toViews(List<Complaint> list, UUID viewerId) {
    if (list.isEmpty()) return List.of();
    CatalogIndex idx =
        catalog.index(
            collect(list, Complaint::getTypeId),
            collect(list, Complaint::getSubtypeId),
            collect(list, Complaint::getLocationId));
    Map<UUID, List<ComplaintPhotoView>> photosById =
        photos.findByComplaintIdInOrderByOrdering(collect(list, Complaint::getId)).stream()
            .collect(
                Collectors.groupingBy(
                    ComplaintPhoto::getComplaintId,
                    Collectors.mapping(ComplaintPhotoView::of, Collectors.toList())));
    return list.stream()
        .map(
            c ->
                ComplaintView.of(
                    c,
                    idx,
                    photosById.getOrDefault(c.getId(), List.of()),
                    c.isAuthor(viewerId) && c.getStatus() == ComplaintStatus.OPEN))
        .toList();
  }

  private static Set<UUID> collect(List<Complaint> list, Function<Complaint, UUID> f) {
    return list.stream().map(f).collect(Collectors.toSet());
  }
}
