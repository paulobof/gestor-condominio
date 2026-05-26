package br.com.condominio.feature.role;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permission")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "code"})
public class Permission {

  @Id private Short id;

  @Column(name = "code", nullable = false, unique = true, length = 60)
  @Enumerated(EnumType.STRING)
  private PermissionCode code;

  @Column(name = "label", nullable = false, length = 80)
  private String label;
}
