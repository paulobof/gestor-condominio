package br.com.condominio.feature.role;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "name"})
public class Role {

  @Id private Short id;

  @Column(name = "name", nullable = false, unique = true, length = 20)
  @Enumerated(EnumType.STRING)
  private RoleName name;

  @Column(name = "label", nullable = false, length = 40)
  private String label;

  @Column(name = "max_holders")
  private Short maxHolders;

  @Column(name = "assignable", nullable = false)
  private boolean assignable;
}
