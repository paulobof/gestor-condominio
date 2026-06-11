package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.CreateUserRequest;
import br.com.condominio.feature.access.dto.CreatedUserResponse;
import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UserAccessRow;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de acessos: atribuir/remover roles geríveis (assignable) a usuários, com validação de
 * {@code max_holders} e auditoria imutável. Autorização ({@code ROLE_ASSIGN}) é feita no
 * controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessService {

  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleAssignmentLogRepository logRepo;
  private final AccessUserRepository userSearchRepo;
  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final PasswordEncoder encoder;
  private final ProvisionalPasswordGenerator passwordGenerator;

  @Transactional(readOnly = true)
  public List<AssignableRoleView> assignableRoles() {
    return roleRepo.findByAssignableTrue().stream()
        .map(r -> new AssignableRoleView(r.getId(), r.getName().name(), r.getLabel()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AssignableRoleView> creatableRoles() {
    List<Role> roles = new ArrayList<>(roleRepo.findByAssignableTrue());
    roleRepo.findByName(RoleName.RESIDENT).ifPresent(roles::add);
    return roles.stream()
        .sorted(Comparator.comparing(Role::getId))
        .map(r -> new AssignableRoleView(r.getId(), r.getName().name(), r.getLabel()))
        .toList();
  }

  private Set<Short> creatableRoleIds() {
    return creatableRoles().stream()
        .map(AssignableRoleView::id)
        .collect(Collectors.toCollection(HashSet::new));
  }

  @Transactional(readOnly = true)
  public Page<UserAccessRow> listUsers(String q, Pageable pageable) {
    String term = (q == null || q.isBlank()) ? null : q.trim();
    Page<UserSearchResult> page =
        (term == null)
            ? userSearchRepo.findActivePageAll(pageable)
            : userSearchRepo.findActivePageByTerm(term, pageable);
    List<UUID> ids = page.getContent().stream().map(UserSearchResult::id).toList();

    Map<Short, String> labelById =
        roleRepo.findByAssignableTrue().stream()
            .collect(Collectors.toMap(Role::getId, Role::getLabel));

    Map<UUID, List<RoleBadge>> rolesByUser = new HashMap<>();
    if (!ids.isEmpty()) {
      for (UserRole ur : userRoleRepo.findById_UserIdIn(ids)) {
        String label = labelById.get(ur.getId().getRoleId());
        if (label == null) {
          continue; // role não-gerível: não vira badge
        }
        rolesByUser
            .computeIfAbsent(ur.getId().getUserId(), k -> new ArrayList<>())
            .add(new RoleBadge(ur.getId().getRoleId(), label));
      }
    }
    rolesByUser.values().forEach(list -> list.sort(Comparator.comparing(RoleBadge::label)));

    return page.map(
        u ->
            new UserAccessRow(
                u.id(),
                u.displayName(),
                u.unitLabel(),
                u.phone(),
                rolesByUser.getOrDefault(u.id(), List.of())));
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

  @Transactional
  public CreatedUserResponse createUser(UUID actorId, CreateUserRequest req) {
    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
    Set<Short> creatable = creatableRoleIds();
    for (Short rid : req.roleIds()) {
      if (!creatable.contains(rid)) {
        throw new AccessException(
            "ROLE_NOT_CREATABLE", "Perfil não pode ser atribuído no cadastro.");
      }
    }
    String plain = passwordGenerator.generate();
    User user =
        User.newActiveByAdmin(
            req.unitId(),
            req.fullName().trim(),
            req.phone().trim(),
            encoder.encode(plain),
            (short) 1);
    user = userRepo.save(user);
    emailRepo.save(UserEmail.primary(user.getId(), req.email().trim()));
    Instant now = Instant.now();
    for (Short rid : req.roleIds()) {
      userRoleRepo.save(new UserRole(new UserRoleId(user.getId(), rid), now, actorId));
      logRepo.save(RoleAssignmentLog.assign(user.getId(), rid, actorId));
    }
    log.info("Admin {} criou usuário {}", actorId, user.getId());
    return new CreatedUserResponse(user.getId(), user.getFullName(), plain);
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
