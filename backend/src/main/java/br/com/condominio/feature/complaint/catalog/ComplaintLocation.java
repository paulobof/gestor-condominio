package br.com.condominio.feature.complaint.catalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint_location")
@DynamicInsert
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE complaint_location SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ComplaintLocation extends CatalogEntry {

  private ComplaintLocation(String name, int sortOrder) {
    super(name, sortOrder);
  }

  public static ComplaintLocation create(String name, int sortOrder) {
    return new ComplaintLocation(name, sortOrder);
  }
}
