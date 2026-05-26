package br.com.condominio.feature.role;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
  private final RoleRepository roleRepo;

  @PersistenceContext private EntityManager entityManager;

  /**
   * Effective permissions = roles' default permissions ∪ active individual grants. Returns
   * permission codes as strings (e.g., "USER_VIEW").
   */
  @Transactional(readOnly = true)
  public Set<String> effectivePermissions(UUID userId) {
    @SuppressWarnings("unchecked")
    List<String> codes =
        entityManager
            .createNativeQuery(
                """
                SELECT DISTINCT p.code
                  FROM permission p
                  JOIN role_permission rp ON rp.permission_id = p.id
                  JOIN user_role ur ON ur.role_id = rp.role_id
                 WHERE ur.user_id = :uid
                UNION
                SELECT DISTINCT p.code
                  FROM permission p
                  JOIN user_permission_grant g ON g.permission_id = p.id
                 WHERE g.user_id = :uid AND g.revoked_at IS NULL
                """)
            .setParameter("uid", userId)
            .getResultList();
    return new LinkedHashSet<>(codes);
  }

  @Transactional(readOnly = true)
  public List<RoleName> roles(UUID userId) {
    return userRoleRepo.findById_UserId(userId).stream()
        .flatMap(ur -> roleRepo.findById(ur.getId().getRoleId()).stream())
        .map(Role::getName)
        .toList();
  }
}
