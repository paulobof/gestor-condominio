package br.com.condominio.feature.complaint.catalog;

import java.util.Map;
import java.util.UUID;

/** Nomes do catálogo resolvidos em lote, para montar views e o texto do lote sem N+1. */
public record CatalogIndex(
    Map<UUID, ComplaintType> types,
    Map<UUID, ComplaintSubtype> subtypes,
    Map<UUID, ComplaintLocation> locations) {

  private static final String MISSING = "—";

  public String typeName(UUID id) {
    ComplaintType t = types.get(id);
    return t == null ? MISSING : t.getName();
  }

  public int typeOrder(UUID id) {
    ComplaintType t = types.get(id);
    return t == null ? Integer.MAX_VALUE : t.getSortOrder();
  }

  public String subtypeName(UUID id) {
    ComplaintSubtype s = subtypes.get(id);
    return s == null ? MISSING : s.getName();
  }

  public String locationName(UUID id) {
    ComplaintLocation l = locations.get(id);
    return l == null ? MISSING : l.getName();
  }
}
