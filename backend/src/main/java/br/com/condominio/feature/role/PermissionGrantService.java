package br.com.condominio.feature.role;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concessão idempotente de permission direta a um usuário. Compartilhado entre a aprovação de
 * cadastro (master) e a aprovação de posse de unidade — ambas concedem {@code RESIDENT_MANAGE}.
 */
@Service
@RequiredArgsConstructor
public class PermissionGrantService {

  private final PermissionRepository permissionRepo;
  private final UserPermissionGrantRepository grantRepo;

  /** Concede a permission ao usuário se ainda não houver grant ativo (não duplica). */
  @Transactional
  public void grantIfAbsent(UUID userId, PermissionCode code, UUID grantedByUserId) {
    Permission perm =
        permissionRepo
            .findByCode(code)
            .orElseThrow(() -> new IllegalStateException(code + " permission missing"));
    boolean alreadyGranted =
        grantRepo.findByUserIdAndRevokedAtIsNull(userId).stream()
            .anyMatch(g -> g.getPermissionId().equals(perm.getId()));
    if (!alreadyGranted) {
      grantRepo.save(UserPermissionGrant.grant(userId, perm.getId(), grantedByUserId));
    }
  }
}
