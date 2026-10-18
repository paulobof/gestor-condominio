package br.com.condominio.feature.complaint.catalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint_type")
@DynamicInsert
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE complaint_type SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ComplaintType extends CatalogEntry {

  private ComplaintType(String name, int sortOrder) {
    super(name, sortOrder);
  }

  public static ComplaintType create(String name, int sortOrder) {
    return new ComplaintType(name, sortOrder);
  }
}
