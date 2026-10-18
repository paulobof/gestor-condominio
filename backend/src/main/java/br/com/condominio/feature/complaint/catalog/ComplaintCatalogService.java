package br.com.condominio.feature.complaint.catalog;

import br.com.condominio.feature.complaint.ComplaintException;
import br.com.condominio.feature.complaint.catalog.dto.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintCatalogService {

  private final ComplaintTypeRepository types;
  private final ComplaintSubtypeRepository subtypes;
  private final ComplaintLocationRepository locations;

  /**
   * Catálogo para o formulário (só ativos) ou para a tela de gestão (tudo). No modo "só ativos",
   * tipo sem subtipo ativo some: não há como abrir chamado nele.
   */
  @Transactional(readOnly = true)
  public CatalogView catalog(boolean includeInactive) {
    Map<UUID, List<CatalogItemView>> subsByType =
        subtypes.findAllByOrderBySortOrderAscNameAsc().stream()
            .filter(s -> includeInactive || s.isActive())
            .collect(
                Collectors.groupingBy(
                    ComplaintSubtype::getTypeId,
                    LinkedHashMap::new,
                    Collectors.mapping(CatalogItemView::of, Collectors.toList())));
    List<CatalogTypeView> typeViews =
        types.findAllByOrderBySortOrderAscNameAsc().stream()
            .filter(t -> includeInactive || t.isActive())
            .map(t -> typeView(t, subsByType.getOrDefault(t.getId(), List.of())))
            .filter(t -> includeInactive || !t.subtypes().isEmpty())
            .toList();
    List<CatalogItemView> locs =
        locations.findAllByOrderBySortOrderAscNameAsc().stream()
            .filter(l -> includeInactive || l.isActive())
            .map(CatalogItemView::of)
            .toList();
    return new CatalogView(typeViews, locs);
  }

  @Transactional
  public CatalogTypeView createType(CatalogItemRequest r) {
    String name = r.name().strip();
    if (types.existsByNameIgnoreCase(name)) throw duplicate();
    ComplaintType t = ComplaintType.create(name, r.sortOrder());
    t.update(name, r.sortOrder(), r.active());
    return typeView(types.save(t), List.of());
  }

  @Transactional
  public CatalogTypeView updateType(UUID id, CatalogItemRequest r) {
    ComplaintType t = types.findById(id).orElseThrow(ComplaintCatalogService::notFound);
    String name = r.name().strip();
    if (types.existsByNameIgnoreCaseAndIdNot(name, id)) throw duplicate();
    t.update(name, r.sortOrder(), r.active());
    List<CatalogItemView> subs =
        subtypes.findByTypeIdOrderBySortOrderAscNameAsc(id).stream()
            .map(CatalogItemView::of)
            .toList();
    return typeView(t, subs);
  }

  @Transactional
  public CatalogItemView createSubtype(UUID typeId, CatalogItemRequest r) {
    types.findById(typeId).orElseThrow(ComplaintCatalogService::notFound);
    String name = r.name().strip();
    if (subtypes.existsByTypeIdAndNameIgnoreCase(typeId, name)) throw duplicate();
    ComplaintSubtype s = ComplaintSubtype.create(typeId, name, r.sortOrder());
    s.update(name, r.sortOrder(), r.active());
    return CatalogItemView.of(subtypes.save(s));
  }

  @Transactional
  public CatalogItemView updateSubtype(UUID id, CatalogItemRequest r) {
    ComplaintSubtype s = subtypes.findById(id).orElseThrow(ComplaintCatalogService::notFound);
    String name = r.name().strip();
    if (subtypes.existsByTypeIdAndNameIgnoreCaseAndIdNot(s.getTypeId(), name, id)) {
      throw duplicate();
    }
    s.update(name, r.sortOrder(), r.active());
    return CatalogItemView.of(s);
  }

  @Transactional
  public CatalogItemView createLocation(CatalogItemRequest r) {
    String name = r.name().strip();
    if (locations.existsByNameIgnoreCase(name)) throw duplicate();
    ComplaintLocation l = ComplaintLocation.create(name, r.sortOrder());
    l.update(name, r.sortOrder(), r.active());
    return CatalogItemView.of(locations.save(l));
  }

  @Transactional
  public CatalogItemView updateLocation(UUID id, CatalogItemRequest r) {
    ComplaintLocation l = locations.findById(id).orElseThrow(ComplaintCatalogService::notFound);
    String name = r.name().strip();
    if (locations.existsByNameIgnoreCaseAndIdNot(name, id)) throw duplicate();
    l.update(name, r.sortOrder(), r.active());
    return CatalogItemView.of(l);
  }

  /**
   * Valida a escolha de tipo/subtipo/local. {@code grandfathered}: ids que o chamado já usa — um
   * item desativado depois continua aceito para ele, senão o admin não conseguiria editar só a
   * descrição de um chamado antigo.
   */
  @Transactional(readOnly = true)
  public void requireUsable(UUID typeId, UUID subtypeId, UUID locationId, Set<UUID> grandfathered) {
    ComplaintType t = types.findById(typeId).orElseThrow(() -> invalid("Tipo inexistente."));
    ComplaintSubtype s =
        subtypes.findById(subtypeId).orElseThrow(() -> invalid("Subtipo inexistente."));
    ComplaintLocation l =
        locations.findById(locationId).orElseThrow(() -> invalid("Local inexistente."));
    if (!s.getTypeId().equals(t.getId())) {
      throw invalid("O subtipo não pertence ao tipo escolhido.");
    }
    for (CatalogEntry e : List.<CatalogEntry>of(t, s, l)) {
      if (!e.isActive() && !grandfathered.contains(e.getId())) {
        throw invalid("\"" + e.getName() + "\" não está mais disponível.");
      }
    }
  }

  @Transactional(readOnly = true)
  public CatalogIndex index(Set<UUID> typeIds, Set<UUID> subtypeIds, Set<UUID> locationIds) {
    return new CatalogIndex(
        byId(types.findAllById(typeIds)),
        byId(subtypes.findAllById(subtypeIds)),
        byId(locations.findAllById(locationIds)));
  }

  private static <T extends CatalogEntry> Map<UUID, T> byId(List<T> list) {
    return list.stream().collect(Collectors.toMap(CatalogEntry::getId, Function.identity()));
  }

  private static CatalogTypeView typeView(ComplaintType t, List<CatalogItemView> subs) {
    return new CatalogTypeView(t.getId(), t.getName(), t.isActive(), t.getSortOrder(), subs);
  }

  private static ComplaintException notFound() {
    return new ComplaintException("NOT_FOUND", "Item do catálogo não encontrado.");
  }

  private static ComplaintException duplicate() {
    return new ComplaintException("DUPLICATE_NAME", "Já existe um item com esse nome.");
  }

  private static ComplaintException invalid(String msg) {
    return new ComplaintException("CATALOG_INVALID", msg);
  }
}
