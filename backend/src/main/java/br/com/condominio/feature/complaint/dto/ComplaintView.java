package br.com.condominio.feature.complaint.dto;

import br.com.condominio.feature.complaint.Complaint;
import br.com.condominio.feature.complaint.ComplaintStatus;
import br.com.condominio.feature.complaint.catalog.CatalogIndex;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Chamado como o morador vê (próprio ou no mural). Não tem autor nem unidade por construção: o
 * mural usa este mesmo DTO, então nenhum campo aqui pode identificar quem abriu.
 */
public record ComplaintView(
    UUID id,
    Long code,
    UUID typeId,
    String typeName,
    UUID subtypeId,
    String subtypeName,
    UUID locationId,
    String locationName,
    String locationDetail,
    String description,
    ComplaintStatus status,
    LocalDate dueDate,
    String rejectionReason,
    String resolutionNote,
    Instant createdAt,
    Instant forwardedAt,
    Instant resolvedAt,
    boolean editable,
    List<ComplaintPhotoView> photos) {

  public static ComplaintView of(
      Complaint c, CatalogIndex idx, List<ComplaintPhotoView> photos, boolean editable) {
    return new ComplaintView(
        c.getId(),
        c.getCode(),
        c.getTypeId(),
        idx.typeName(c.getTypeId()),
        c.getSubtypeId(),
        idx.subtypeName(c.getSubtypeId()),
        c.getLocationId(),
        idx.locationName(c.getLocationId()),
        c.getLocationDetail(),
        c.getDescription(),
        c.getStatus(),
        c.getDueDate(),
        c.getRejectionReason(),
        c.getResolutionNote(),
        c.getCreatedAt(),
        c.getForwardedAt(),
        c.getResolvedAt(),
        editable,
        photos);
  }
}
