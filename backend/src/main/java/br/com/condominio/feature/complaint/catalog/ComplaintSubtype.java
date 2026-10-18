package br.com.condominio.feature.complaint.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint_subtype")
@DynamicInsert
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE complaint_subtype SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ComplaintSubtype extends CatalogEntry {

  @Column(name = "type_id", nullable = false, updatable = false)
  private UUID typeId;

  private ComplaintSubtype(UUID typeId, String name, int sortOrder) {
    super(name, sortOrder);
    this.typeId = typeId;
  }

  public static ComplaintSubtype create(UUID typeId, String name, int sortOrder) {
    return new ComplaintSubtype(typeId, name, sortOrder);
  }
}
