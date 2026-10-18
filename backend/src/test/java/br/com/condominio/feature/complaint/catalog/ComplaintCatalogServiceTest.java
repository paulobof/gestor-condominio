package br.com.condominio.feature.complaint.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.complaint.ComplaintException;
import br.com.condominio.feature.complaint.catalog.dto.CatalogItemRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ComplaintCatalogServiceTest {

  private ComplaintTypeRepository types;
  private ComplaintSubtypeRepository subtypes;
  private ComplaintLocationRepository locations;
  private ComplaintCatalogService service;

  @BeforeEach
  void setUp() {
    types = mock(ComplaintTypeRepository.class);
    subtypes = mock(ComplaintSubtypeRepository.class);
    locations = mock(ComplaintLocationRepository.class);
    service = new ComplaintCatalogService(types, subtypes, locations);
    when(types.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
    when(subtypes.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
    when(locations.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
  }

  /** Simula o UUID que o Hibernate atribui no persist. */
  private static <T> T withId(T entity) {
    if (ReflectionTestUtils.getField(entity, "id") == null) {
      ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    }
    return entity;
  }

  private ComplaintType type(String name, boolean active) {
    ComplaintType t = withId(ComplaintType.create(name, 1));
    t.update(name, 1, active);
    return t;
  }

  private ComplaintSubtype subtype(UUID typeId, String name, boolean active) {
    ComplaintSubtype s = withId(ComplaintSubtype.create(typeId, name, 1));
    s.update(name, 1, active);
    return s;
  }

  private ComplaintLocation location(String name, boolean active) {
    ComplaintLocation l = withId(ComplaintLocation.create(name, 1));
    l.update(name, 1, active);
    return l;
  }

  @Test
  void catalog_activeOnly_hidesInactiveItemsAndTypesWithoutActiveSubtypes() {
    ComplaintType manut = type("Manutenção", true);
    ComplaintType vazio = type("Sem subtipos ativos", true);
    ComplaintType inativo = type("Inativo", false);
    when(types.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(manut, vazio, inativo));
    when(subtypes.findAllByOrderBySortOrderAscNameAsc())
        .thenReturn(
            List.of(
                subtype(manut.getId(), "Pintura", true),
                subtype(manut.getId(), "Velho", false),
                subtype(vazio.getId(), "Desligado", false),
                subtype(inativo.getId(), "X", true)));
    when(locations.findAllByOrderBySortOrderAscNameAsc())
        .thenReturn(List.of(location("Torre 1", true), location("Antigo", false)));

    var view = service.catalog(false);

    assertThat(view.types()).extracting("name").containsExactly("Manutenção");
    assertThat(view.types().get(0).subtypes()).extracting("name").containsExactly("Pintura");
    assertThat(view.locations()).extracting("name").containsExactly("Torre 1");
  }

  @Test
  void catalog_includeInactive_returnsEverything() {
    when(types.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(type("Inativo", false)));
    when(subtypes.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of());
    when(locations.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of());

    assertThat(service.catalog(true).types()).hasSize(1);
  }

  @Test
  void createType_duplicateName_isConflict() {
    when(types.existsByNameIgnoreCase("Limpeza")).thenReturn(true);
    assertThatThrownBy(() -> service.createType(new CatalogItemRequest(" Limpeza ", 1, true)))
        .isInstanceOf(ComplaintException.class)
        .extracting("code")
        .isEqualTo("DUPLICATE_NAME");
  }

  @Test
  void updateType_renamesReordersAndDeactivates() {
    ComplaintType t = type("Limpeza", true);
    when(types.findById(t.getId())).thenReturn(Optional.of(t));
    when(subtypes.findByTypeIdOrderBySortOrderAscNameAsc(t.getId())).thenReturn(List.of());

    var view = service.updateType(t.getId(), new CatalogItemRequest("Higiene", 7, false));

    assertThat(view.name()).isEqualTo("Higiene");
    assertThat(view.sortOrder()).isEqualTo(7);
    assertThat(view.active()).isFalse();
  }

  @Test
  void createSubtype_unknownType_isNotFound() {
    UUID typeId = UUID.randomUUID();
    when(types.findById(typeId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createSubtype(typeId, new CatalogItemRequest("X", 1, true)))
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void requireUsable_subtypeFromAnotherType_isCatalogInvalid() {
    ComplaintType a = type("A", true);
    ComplaintType b = type("B", true);
    ComplaintSubtype ofB = subtype(b.getId(), "s", true);
    ComplaintLocation loc = location("L", true);
    when(types.findById(a.getId())).thenReturn(Optional.of(a));
    when(subtypes.findById(ofB.getId())).thenReturn(Optional.of(ofB));
    when(locations.findById(loc.getId())).thenReturn(Optional.of(loc));

    assertThatThrownBy(() -> service.requireUsable(a.getId(), ofB.getId(), loc.getId(), Set.of()))
        .extracting("code")
        .isEqualTo("CATALOG_INVALID");
  }

  @Test
  void requireUsable_inactiveItem_rejectedUnlessGrandfathered() {
    ComplaintType t = type("A", false);
    ComplaintSubtype s = subtype(t.getId(), "s", true);
    ComplaintLocation loc = location("L", true);
    when(types.findById(t.getId())).thenReturn(Optional.of(t));
    when(subtypes.findById(s.getId())).thenReturn(Optional.of(s));
    when(locations.findById(loc.getId())).thenReturn(Optional.of(loc));

    assertThatThrownBy(() -> service.requireUsable(t.getId(), s.getId(), loc.getId(), Set.of()))
        .extracting("code")
        .isEqualTo("CATALOG_INVALID");
    // já em uso pelo chamado: continua válido (Review Focus #2)
    service.requireUsable(t.getId(), s.getId(), loc.getId(), Set.of(t.getId()));
  }

  @Test
  void index_resolvesNamesWithFallback() {
    ComplaintType t = type("Manutenção", true);
    when(types.findAllById(any())).thenReturn(List.of(t));
    when(subtypes.findAllById(any())).thenReturn(List.of());
    when(locations.findAllById(any())).thenReturn(List.of());

    CatalogIndex idx = service.index(Set.of(t.getId()), Set.of(), Set.of());

    assertThat(idx.typeName(t.getId())).isEqualTo("Manutenção");
    assertThat(idx.subtypeName(UUID.randomUUID())).isEqualTo("—");
  }
}
