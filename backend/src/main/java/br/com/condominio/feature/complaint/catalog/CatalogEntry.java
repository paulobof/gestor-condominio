package br.com.condominio.feature.complaint.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Base dos itens do catálogo de chamados: nome, ativo e ordem de exibição. */
@MappedSuperclass
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public abstract class CatalogEntry {

  @Id @GeneratedValue @EqualsAndHashCode.Include @ToString.Include private UUID id;
  @Version private Long version;

  @ToString.Include
  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected CatalogEntry(String name, int sortOrder) {
    this.name = name.strip();
    this.sortOrder = sortOrder;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** Nova posição na lista de exibição (só a ordem muda; nome e ativo ficam). */
  public void reorder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void update(String name, int sortOrder, boolean active) {
    this.name = name.strip();
    this.sortOrder = sortOrder;
    this.active = active;
  }
}
