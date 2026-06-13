package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberDetail;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import br.com.condominio.feature.user.event.MemberEmailChangedEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnitMemberServiceTest {

  private static final UUID MASTER = UUID.randomUUID();
  private static final UUID UNIT = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();

  @Mock private UserRepository userRepo;
  @Mock private UserEmailRepository emailRepo;
  @Mock private UserRoleRepository userRoleRepo;
  @Mock private RoleRepository roleRepo;
  @Mock private UserProvisioning provisioning;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private UnitMemberService service;

  private User masterInUnit() {
    User m = mock(User.class);
    when(m.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(m.isUnitMaster()).thenReturn(true);
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
    when(master.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(master.isUnitMaster()).thenReturn(true);
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

  // ===== FIX 2 + FIX 3: requireMaster guarda ordenada =====

  @Test
  void create_masterDisabled_throwsUserNotActive() {
    User master = mock(User.class);
    when(master.getStatus()).thenReturn(UserStatus.DISABLED);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria", "Maria", "maria@x.com", "11999998888", null, null, false);
    assertThatThrownBy(() -> service.createMember(MASTER, req))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_ACTIVE");
  }

  @Test
  void create_masterNotUnitMaster_throwsNotAMaster() {
    User master = mock(User.class);
    when(master.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(master.isUnitMaster()).thenReturn(false);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    CreateUnitMemberRequest req =
        new CreateUnitMemberRequest(
            "Maria", "Maria", "maria@x.com", "11999998888", null, null, false);
    assertThatThrownBy(() -> service.createMember(MASTER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("NOT_A_MASTER");
  }

  @Test
  void update_masterWithoutUnit_throwsMasterHasNoUnit() {
    User master = mock(User.class);
    when(master.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(master.isUnitMaster()).thenReturn(true);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest("Maria", "Maria", "11999998888", "m@x.com", null, null);
    assertThatThrownBy(() -> service.updateMember(MASTER, MEMBER, req))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MASTER_HAS_NO_UNIT");
  }

  @Test
  void delete_masterWithoutUnit_throwsMasterHasNoUnit() {
    User master = mock(User.class);
    when(master.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(master.isUnitMaster()).thenReturn(true);
    when(master.getUnitId()).thenReturn(null);
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));

    assertThatThrownBy(() -> service.deleteMember(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MASTER_HAS_NO_UNIT");
  }

  // ===== Notificação WhatsApp ao alterar e-mail do morador =====

  private User memberInUnit() {
    User m = mock(User.class);
    when(m.getUnitId()).thenReturn(UNIT);
    when(m.isUnitMaster()).thenReturn(false);
    when(m.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(m.getPhone()).thenReturn("11999998888");
    when(m.getGreetingName()).thenReturn("Carlos");
    return m;
  }

  @Test
  void updateMember_emailDiferente_publicaEventoMemberEmailChanged() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = memberInUnit();
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    // e-mail atual do morador
    UserEmail currentEmail = mock(UserEmail.class);
    when(currentEmail.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(MEMBER)).thenReturn(Optional.of(currentEmail));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest(
            "Carlos Silva", "Carlos", "11999998888", "new@x.com", null, null);
    service.updateMember(MASTER, MEMBER, req);

    ArgumentCaptor<MemberEmailChangedEvent> captor =
        ArgumentCaptor.forClass(MemberEmailChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    MemberEmailChangedEvent event = captor.getValue();
    assertThat(event.memberUserId()).isEqualTo(MEMBER);
    assertThat(event.phone()).isEqualTo("11999998888");
    assertThat(event.greetingName()).isEqualTo("Carlos");
  }

  @Test
  void updateMember_mesmoEmail_naoPublicaEvento() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = memberInUnit();
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    // e-mail atual igual ao novo (case-insensitive)
    UserEmail currentEmail = mock(UserEmail.class);
    when(currentEmail.getEmail()).thenReturn("same@x.com");
    when(emailRepo.findPrimaryByUserId(MEMBER)).thenReturn(Optional.of(currentEmail));

    UpdateUnitMemberRequest req =
        new UpdateUnitMemberRequest(
            "Carlos Silva", "Carlos", "11999998888", "Same@x.com", null, null);
    service.updateMember(MASTER, MEMBER, req);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void getMemberDetail_inMyUnit_returnsDetail() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getId()).thenReturn(MEMBER);
    when(member.getFullName()).thenReturn("Bia Souza");
    when(member.getGreetingName()).thenReturn("Bia");
    when(member.getPhone()).thenReturn("+5511988887777");
    when(member.getGender()).thenReturn(Gender.FEMALE);
    when(member.getBirthDate()).thenReturn(java.time.LocalDate.of(1990, 1, 2));
    when(member.getUnitId()).thenReturn(UNIT);
    when(member.isUnitMaster()).thenReturn(false);
    when(member.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));
    UserEmail email = mock(UserEmail.class);
    when(email.getEmail()).thenReturn("bia@x.com");
    when(emailRepo.findPrimaryByUserId(MEMBER)).thenReturn(Optional.of(email));

    UnitMemberDetail detail = service.getMemberDetail(MASTER, MEMBER);

    assertThat(detail.fullName()).isEqualTo("Bia Souza");
    assertThat(detail.email()).isEqualTo("bia@x.com");
    assertThat(detail.gender()).isEqualTo("FEMALE");
    assertThat(detail.birthDate()).isEqualTo(java.time.LocalDate.of(1990, 1, 2));
  }

  @Test
  void getMemberDetail_otherUnit_throwsMemberNotInUnit() {
    User master = masterInUnit();
    when(userRepo.findById(MASTER)).thenReturn(Optional.of(master));
    User member = mock(User.class);
    when(member.getUnitId()).thenReturn(UUID.randomUUID()); // outra unidade
    when(userRepo.findById(MEMBER)).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> service.getMemberDetail(MASTER, MEMBER))
        .isInstanceOf(UnitMemberException.class)
        .extracting("code")
        .isEqualTo("MEMBER_NOT_IN_UNIT");
  }
}
