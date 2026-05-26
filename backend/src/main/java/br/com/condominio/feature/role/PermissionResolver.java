package br.com.condominio.feature.role;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PermissionResolver {

  private final UserRoleRepository userRoleRepo;
  private final UserPermissionGrantRepository userGrantRepo;
  private final RoleRepository roleRepo;
  private final PermissionRepository permissionRepo;

  /**
   * Resolve effective permissions = (∪ role.permissions for role in user.roles) ∪ active grants.
   */
  @Transactional(readOnly = true)
  public Set<String> effectivePermissions(UUID userId) {
    Set<String> result = new LinkedHashSet<>();
    List<UserRole> roles = userRoleRepo.findById_UserId(userId);
    // Por enquanto: vamos listar role_permission via query JPA simples.
    // (Implementação completa requer query nativa cruzando role_permission)
    // Implementacao mais simples: usar query nativa
    return result;
  }

  /** Roles do usuário. */
  @Transactional(readOnly = true)
  public List<RoleName> roles(UUID userId) {
    return userRoleRepo.findById_UserId(userId).stream()
        .flatMap(ur -> roleRepo.findById(ur.getId().getRoleId()).stream())
        .map(Role::getName)
        .toList();
  }
}
