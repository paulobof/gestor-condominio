package br.com.condominio.feature.role;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import lombok.*;

@Entity
@Table(name = "role_permission")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {

  @Id
  @Column(name = "role_id")
  private Short roleId;

  @Id
  @Column(name = "permission_id")
  private Short permissionId;

  public static class RolePermissionId implements Serializable {
    private Short roleId;
    private Short permissionId;

    public RolePermissionId() {}

    public RolePermissionId(Short roleId, Short permissionId) {
      this.roleId = roleId;
      this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RolePermissionId other)) return false;
      return Objects.equals(roleId, other.roleId)
          && Objects.equals(permissionId, other.permissionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(roleId, permissionId);
    }
  }
}
