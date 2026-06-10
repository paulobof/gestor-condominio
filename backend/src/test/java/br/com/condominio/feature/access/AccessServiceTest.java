package br.com.condominio.feature.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import br.com.condominio.feature.access.dto.RoleBadge;
import br.com.condominio.feature.access.dto.UserAccessRow;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();

  @Mock private RoleRepository roleRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private RoleAssignmentLogRepository logRepo;
  @Mock private AccessUserRepository userSearchRepo;
  @Mock private UserRepository userRepo;

  @InjectMocks private AccessService service;

  // lenient(): nem todo teste usa todos os getters (ex.: role não-assignable nunca chama getLabel),
  // e o MockitoExtension é strict por padrão (UnnecessaryStubbingException).
  // doReturn + withSettings().lenient() porque:
  // 1) Role.@EqualsAndHashCode(of="id") chama getId() internamente, e
  // 2) helpers (role/activeUser) devem ser chamados ANTES de when() — não como argumento de
  //    thenReturn() — para evitar "unfinished stubbing" de outro mock em progresso.
  private Role role(short id, String label, Short maxHolders, boolean assignable) {
    Role r = mock(Role.class, withSettings().lenient());
    doReturn(id).when(r).getId();
    doReturn(label).when(r).getLabel();
    doReturn(maxHolders).when(r).getMaxHolders();
    doReturn(assignable).when(r).isAssignable();
    return r;
  }

  private User activeUser() {
    User u = mock(User.class);
    when(u.getStatus()).thenReturn(UserStatus.ACTIVE);
    return u;
  }

  @Test
  void assign_happyPath_savesUserRoleAndLog() {
    Role r = role((short) 6, "Editor do Mural", null, true);
    User target = activeUser();
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(r));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(target));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(false);

    service.assign(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo).save(any(UserRole.class));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }

  @Test
  void assign_alreadyHasRole_isNoOp() {
    Role r = role((short) 6, "Editor do Mural", null, true);
    User target = activeUser();
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(r));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(target));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(true);

    service.assign(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo, never()).save(any());
    verify(logRepo, never()).save(any());
  }

  @Test
  void assign_atMaxHolders_throwsLimit() {
    Role r = role((short) 2, "Conselheiro", (short) 3, true);
    User target = activeUser();
    when(roleRepo.findById((short) 2)).thenReturn(Optional.of(r));
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(target));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 2))).thenReturn(false);
    when(userRoleRepo.countById_RoleId((short) 2)).thenReturn(3L);

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 2))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("ROLE_LIMIT_REACHED");
    verify(userRoleRepo, never()).save(any());
  }

  @Test
  void assign_roleNotAssignable_throws() {
    Role r = role((short) 1, "Síndico", (short) 1, false);
    when(roleRepo.findById((short) 1)).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 1))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("ROLE_NOT_ASSIGNABLE");
  }

  @Test
  void assign_userNotActive_throws() {
    Role r = role((short) 6, "Editor do Mural", null, true);
    User pending = mock(User.class);
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(r));
    when(pending.getStatus()).thenReturn(UserStatus.PENDING_APPROVAL);
    when(userRepo.findById(TARGET)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.assign(ACTOR, TARGET, (short) 6))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_ACTIVE");
  }

  @Test
  void remove_happyPath_deletesAndLogs() {
    Role r = role((short) 6, "Editor do Mural", null, true);
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(r));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(true);

    service.remove(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo).deleteById(new UserRoleId(TARGET, (short) 6));
    verify(logRepo).save(any(RoleAssignmentLog.class));
  }

  @Test
  void remove_notHeld_isNoOp() {
    Role r = role((short) 6, "Editor do Mural", null, true);
    when(roleRepo.findById((short) 6)).thenReturn(Optional.of(r));
    when(userRoleRepo.existsById(new UserRoleId(TARGET, (short) 6))).thenReturn(false);

    service.remove(ACTOR, TARGET, (short) 6);

    verify(userRoleRepo, never()).deleteById(any());
    verify(logRepo, never()).save(any());
  }

  @Test
  void listUsers_mapsRolesIntoBadges() {
    var u1 = new UserSearchResult(TARGET, "Ana Lima", "A-101");
    Role muralEditor = role((short) 6, "Editor do Mural", null, true);
    when(userSearchRepo.findActivePage(null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of(muralEditor));
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET)))
        .thenReturn(List.of(new UserRole(new UserRoleId(TARGET, (short) 6), null, ACTOR)));

    Page<UserAccessRow> page = service.listUsers("", PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
    UserAccessRow row = page.getContent().get(0);
    assertThat(row.id()).isEqualTo(TARGET);
    assertThat(row.displayName()).isEqualTo("Ana Lima");
    assertThat(row.roles()).containsExactly(new RoleBadge((short) 6, "Editor do Mural"));
  }

  @Test
  void listUsers_blankQuery_passesNullTerm_andUserWithoutRoleHasEmptyBadges() {
    var u1 = new UserSearchResult(TARGET, "Bruno Sá", null);
    when(userSearchRepo.findActivePage(null, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(u1)));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());
    when(userRoleRepo.findById_UserIdIn(List.of(TARGET))).thenReturn(List.of());

    Page<UserAccessRow> page = service.listUsers("   ", PageRequest.of(0, 20));

    assertThat(page.getContent().get(0).roles()).isEmpty();
  }

  @Test
  void listUsers_withTerm_trimsAndForwards() {
    when(userSearchRepo.findActivePage("ana", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of());

    // página vazia → ids vazio → findById_UserIdIn não é chamado (por isso não é stubado)
    service.listUsers("  ana  ", PageRequest.of(0, 20));

    verify(userSearchRepo).findActivePage("ana", PageRequest.of(0, 20));
  }

  @Test
  void userRoleIds_filtersToAssignable() {
    Role council = role((short) 2, "Conselheiro", (short) 3, true);
    Role muralEditor = role((short) 6, "Editor do Mural", null, true);
    when(roleRepo.findByAssignableTrue()).thenReturn(List.of(council, muralEditor));
    when(userRoleRepo.findById_UserId(TARGET))
        .thenReturn(
            List.of(
                new UserRole(
                    new UserRoleId(TARGET, (short) 4), null, null), // RESIDENT, não-assignable
                new UserRole(
                    new UserRoleId(TARGET, (short) 6), null, null))); // MURAL_EDITOR, assignable

    assertThat(service.userRoleIds(TARGET)).containsExactly((short) 6);
  }
}
