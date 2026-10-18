package br.com.condominio.feature.complaint.dto;

import br.com.condominio.feature.complaint.ComplaintPhoto;
import java.util.UUID;

public record ComplaintPhotoView(UUID id, int ordering, String contentType) {
  public static ComplaintPhotoView of(ComplaintPhoto p) {
    return new ComplaintPhotoView(p.getId(), p.getOrdering(), p.getContentType());
  }
}
