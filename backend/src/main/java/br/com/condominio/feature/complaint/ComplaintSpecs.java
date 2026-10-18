package br.com.condominio.feature.complaint;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combináveis da fila admin e do mural. Spec nula = filtro ausente. */
final class ComplaintSpecs {

  private ComplaintSpecs() {}

  static Specification<Complaint> statusIn(Collection<ComplaintStatus> statuses) {
    return (root, q, cb) -> root.get("status").in(statuses);
  }

  static Specification<Complaint> typeIs(UUID typeId) {
    return typeId == null ? null : (root, q, cb) -> cb.equal(root.get("typeId"), typeId);
  }

  static Specification<Complaint> locationIs(UUID locationId) {
    return locationId == null
        ? null
        : (root, q, cb) -> cb.equal(root.get("locationId"), locationId);
  }
}
