package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de acessos: atribuir/remover roles geríveis (assignable) a usuários, com validação de
 * {@code max_holders} e auditoria imutável. Autorização ({@code ROLE_ASSIGN}) é feita no
 * controller.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

  private static final int SEARCH_LIMIT = 20;
  private static final int MIN_TERM = 2;

  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleAssignmentLogRepository logRepo;
  private final AccessUserRepository userSearchRepo;
  private final UserRepository userRepo;

  @Transactional(readOnly = true)
  public List<AssignableRoleView> assignableRoles() {
    return roleRepo.findByAssignableTrue().stream()
        .map(r -> new AssignableRoleView(r.getId(), r.getName().name(), r.getLabel()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserSearchResult> searchUsers(String term) {
    if (term == null || term.trim().length() < MIN_TERM) {
      return List.of();
    }
    return userSearchRepo.search(term.trim(), PageRequest.of(0, SEARCH_LIMIT));
  }

  @Transactional(readOnly = true)
  public List<Short> userRoleIds(UUID userId) {
    Set<Short> assignable =
        roleRepo.findByAssignableTrue().stream().map(Role::getId).collect(Collectors.toSet());
    return userRoleRepo.findById_UserId(userId).stream()
        .map(ur -> ur.getId().getRoleId())
        .filter(assignable::contains)
        .toList();
  }

  @Transactional
  public void assign(UUID actorId, UUID targetUserId, short roleId) {
    Role role = requireAssignableRole(roleId);
    User target =
        userRepo
            .findById(targetUserId)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));
    if (target.getStatus() != UserStatus.ACTIVE) {
      throw new AccessException("USER_NOT_ACTIVE", "Usuário não está ativo.");
    }
    UserRoleId id = new UserRoleId(targetUserId, roleId);
    if (userRoleRepo.existsById(id)) {
      return; // idempotente: já tem a role
    }
    // NOTE: check-then-act sob max_holders tem corrida teórica entre count e save. Aceitável:
    // app single-instance, operação rara do síndico; a PK de user_role impede duplicar o mesmo par.
    if (role.getMaxHolders() != null
        && userRoleRepo.countById_RoleId(roleId) >= role.getMaxHolders()) {
      throw new AccessException(
          "ROLE_LIMIT_REACHED",
          "Limite de " + role.getMaxHolders() + " atingido para " + role.getLabel() + ".");
    }
    userRoleRepo.save(new UserRole(id, Instant.now(), actorId));
    logRepo.save(RoleAssignmentLog.assign(targetUserId, roleId, actorId));
  }

  @Transactional
  public void remove(UUID actorId, UUID targetUserId, short roleId) {
    requireAssignableRole(roleId);
    UserRoleId id = new UserRoleId(targetUserId, roleId);
    if (!userRoleRepo.existsById(id)) {
      return; // idempotente: não tinha a role
    }
    userRoleRepo.deleteById(id);
    logRepo.save(RoleAssignmentLog.remove(targetUserId, roleId, actorId));
  }

  private Role requireAssignableRole(short roleId) {
    Role role =
        roleRepo
            .findById(roleId)
            .orElseThrow(() -> new AccessException("ROLE_NOT_FOUND", "Role não encontrada."));
    if (!role.isAssignable()) {
      throw new AccessException("ROLE_NOT_ASSIGNABLE", "Esta role não pode ser gerida por aqui.");
    }
    return role;
  }
}
