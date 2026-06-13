package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitMemberServiceTest {

  private static final UUID MASTER = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();

  @Mock private UserRepository userRepo;
  @Mock private UserEmailRepository emailRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private RoleRepository roleRepo;
  @Mock private UserProvisioning provisioning;

  @InjectMocks private UnitMemberService service;

  private User masterInUnit() {
    User m = mock(User.class);
    when(m.getUnitId()).thenReturn(UNIT);
    return m;
  }

  @Test
  void listMyUnitMembers_scopesToMyUnit() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getId()).thenReturn(MEMBER);
    when(member.getFullName()).thenReturn("Maria");
    when(member.getGreetingName()).thenReturn("Maria");
    when(member.getPhone()).thenReturn("11999998888");
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findByUnitIdAndStatusNotAndIsUnitMasterFalse(UNIT, UserStatus.ANONYMIZED))
        .thenReturn(List.of(member));
    UserEmail e = mock(UserEmail.class);
    when(e.getEmail()).thenReturn("maria@x.com");
    when(emailRepo.findPrimaryByUserId(MEMBER)).thenReturn(Optional.of(e));

    List<UnitMemberResponse> out = service.listMyUnitMembers(MASTER);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).email()).isEqualTo("maria@x.com");
  }

  @Test
  void listMyUnitMembers_masterWithoutUnit_returnsEmpty() {
    User master = mock(User.class);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    assertThat(service.listMyUnitMembers(MASTER)).isEmpty();
    verify(userRepo, never()).findByUnitIdAndStatusNotAndIsUnitMasterFalse(any(), any());
  }

  @Test
  void createMember_happyPath_createsResidentAndReturnsProvisionalPassword() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    Role resident = mock(Role.class);
    when(resident.getId()).thenReturn((short) 4);
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(resident));
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(MEMBER);
    when(saved.getFullName()).thenReturn("Maria Silva");
    when(provisioning.createActiveUser(UNIT, "Maria Silva", "11999998888", "maria@x.com"))
        .thenReturn(new UserProvisioning.Provisioned(saved, "Abc123!xYZ09__a"));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria Silva", "Maria", "maria@x.com", "11999998888", null, null, false);
    CreatedUnitMemberResponse out = service.createMember(MASTER, req);

    assertThat(out.password()).isEqualTo("Abc123!xYZ09__a");
    assertThat(out.id()).isEqualTo(MEMBER);
    verify(userRoleRepo).save(any(UserRole.class));
  }

  @Test
  void createMember_masterWithoutUnit_throws() {
    User master = mock(User.class);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria", "Maria", "maria@x.com", "11999998888", null, null, false);
    assertThatThrownBy(() -> service.createMember(MASTER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MASTER_HAS_NO_UNIT");
    verify(provisioning, never()).createActiveUser(any(), any(), any(), any());
  }

  @Test
  void updateMember_inMyUnit_updatesProfileAndEmail() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UNIT);
    when(member.isUnitMaster()).thenReturn(false);
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest(
            "Maria Nova", "Maria", "11999998888", "nova@x.com", Gender.FEMALE, null);
    service.updateMember(MASTER, MEMBER, req);

    verify(provisioning).changePrimaryEmail(MEMBER, "nova@x.com");
    verify(member)
        .updateProfile(
            eq("Maria Nova"),
            eq("Maria"),
            eq("11999998888"),
            eq(UNIT),
            eq(Gender.FEMALE),
            eq(null));
  }

  @Test
  void updateMember_otherUnit_throwsForbidden() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UUID.randomUUID()); // outra unidade
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest("X", "X", "11999998888", "x@x.com", null, null);
    assertThatThrownBy(() -> service.updateMember(MASTER, MEMBER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
    verify(provisioning, never()).changePrimaryEmail(any(), any());
  }

  @Test
  void deleteMember_inMyUnit_softDeletes() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UNIT);
    when(member.isUnitMaster()).thenReturn(false);
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    service.deleteMember(MASTER, MEMBER);

    verify(provisioning).softDelete(member, MEMBER);
  }

  @Test
  void deleteMember_targetIsMaster_throwsForbidden() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User other = mock(User.class);
    when(other.getUnitId()).thenReturn(UNIT);
    when(other.isUnitMaster()).thenReturn(true); // é master, não morador
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.deleteMember(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
    verify(provisioning, never()).softDelete(any(), any());
  }

  @Test
  void deleteMember_notFound_throws() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    when(userRepo.findById(MEMBER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteMember(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
  }
}
