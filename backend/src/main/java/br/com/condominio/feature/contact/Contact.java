package br.com.condominio.feature.contact;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Telefone útil do condomínio (portaria, serviços, emergências). Soft delete. */
@Entity
@Table(name = "contact")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE contact SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Contact {

  @Id @GeneratedValue @ToString.Include private UUID id;

  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 60)
  private String category;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "is_24h", nullable = false)
  private boolean is24h;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static Contact create(
      String name, String category, String phone, String notes, boolean is24h) {
    Contact c = new Contact();
    c.name = name;
    c.category = category;
    c.phone = phone;
    c.notes = notes;
    c.is24h = is24h;
    return c;
  }

  public void edit(String name, String category, String phone, String notes, boolean is24h) {
    this.name = name;
    this.category = category;
    this.phone = phone;
    this.notes = notes;
    this.is24h = is24h;
  }
}
