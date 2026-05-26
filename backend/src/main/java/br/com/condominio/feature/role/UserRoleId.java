package br.com.condominio.feature.role;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserRoleId implements Serializable {

  private UUID userId;
  private Short roleId;

  public UserRoleId() {}

  public UserRoleId(UUID userId, Short roleId) {
    this.userId = userId;
    this.roleId = roleId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Short getRoleId() {
    return roleId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserRoleId other)) return false;
    return Objects.equals(userId, other.userId) && Objects.equals(roleId, other.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, roleId);
  }
}
