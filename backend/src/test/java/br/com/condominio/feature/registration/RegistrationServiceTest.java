package br.com.condominio.feature.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.event.UnitJoinRequestedEvent;
import br.com.condominio.feature.role.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {

  private UnitRepository unitRepo;
  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private RoleRepository roleRepo;
  private UserRoleRepository userRoleRepo;
  private ConsentDocumentRepository consentRepo;
  private PasswordEncoder encoder;
  private PermissionGrantService permissionGrants;
  private org.springframework.context.ApplicationEventPublisher events;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    unitRepo = mock(UnitRepository.class);
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    roleRepo = mock(RoleRepository.class);
    userRoleRepo = mock(UserRoleRepository.class);
    consentRepo = mock(ConsentDocumentRepository.class);
    encoder = mock(PasswordEncoder.class);
    permissionGrants = mock(PermissionGrantService.class);
    events = mock(org.springframework.context.ApplicationEventPublisher.class);
    service =
        new RegistrationService(
            unitRepo,
            userRepo,
            emailRepo,
            roleRepo,
            userRoleRepo,
            consentRepo,
            encoder,
            permissionGrants,
            events);
  }

  @Test
  void registerMaster_whenUnitHasNoMaster_activatesImmediatelyAsMaster() {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com")).thenReturn(Optional.empty());
    Unit unit = newInstance(Unit.class);
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "code", "702C");
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    when(consentRepo.findByVersion("1.0.0")).thenReturn(Optional.of(newConsent("1.0.0")));
    when(encoder.encode(any())).thenReturn("hashed");
    Role role = newInstance(Role.class);
    setField(role, "id", (short) 4);
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(role));
    when(userRepo.save(any()))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setField(u, "id", UUID.randomUUID());
              return u;
            });

    var resp = service.registerMaster(baseReq(), "127.0.0.1");

    assertThat(resp.status()).isEqualTo("ACTIVE");
    assertThat(unit.getMasterUserId()).isNotNull();
    verify(emailRepo).save(any());
    verify(userRoleRepo).save(any());
    // grantedBy DEVE ser null: o banco proíbe auto-concessão (chk_grant_self).
    verify(permissionGrants)
        .grantIfAbsent(
            any(), eq(PermissionCode.RESIDENT_MANAGE), org.mockito.ArgumentMatchers.isNull());
  }

  @Test
  void registerMaster_whenUnitAlreadyHasMaster_createsPendingRequestAndNotifiesMaster() {
    UUID masterId = UUID.randomUUID();
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com")).thenReturn(Optional.empty());
    Unit unit = newInstance(Unit.class);
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "code", "702C");
    setField(unit, "masterUserId", masterId);
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    when(consentRepo.findByVersion("1.0.0")).thenReturn(Optional.of(newConsent("1.0.0")));
    when(encoder.encode(any())).thenReturn("hashed");
    Role role = newInstance(Role.class);
    setField(role, "id", (short) 4);
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(role));
    User master = newInstance(User.class);
    setField(master, "id", masterId);
    setField(master, "greetingName", "Ana");
    setField(master, "phone", "+5511988887777");
    when(userRepo.findById(masterId)).thenReturn(Optional.of(master));
    when(userRepo.save(any()))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setField(u, "id", UUID.randomUUID());
              return u;
            });

    var resp = service.registerMaster(baseReq(), "127.0.0.1");

    assertThat(resp.status()).isEqualTo("PENDING_APPROVAL");
    // O master da unidade nao muda.
    assertThat(unit.getMasterUserId()).isEqualTo(masterId);
    // Pedido nao vira master e nao ganha gestao da unidade.
    verify(permissionGrants, never()).grantIfAbsent(any(), any(), any());
    verify(events).publishEvent(any(UnitJoinRequestedEvent.class));
  }

  @Test
  void rejectsWhenEmailAlreadyExists() {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com"))
        .thenReturn(Optional.of(newInstance(UserEmail.class)));
    var req = baseReq();
    assertThatThrownBy(() -> service.registerMaster(req, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("e-mail");
  }

  @Test
  void approve_grantsResidentManageToMaster() {
    UUID masterUserId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    User user = mock(User.class);
    when(user.getId()).thenReturn(masterUserId);
    when(user.getUnitId()).thenReturn(unitId);
    when(user.isUnitMaster()).thenReturn(true);
    when(userRepo.findById(masterUserId)).thenReturn(Optional.of(user));

    Unit unit = mock(Unit.class);
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(unit));

    service.approve(masterUserId, approverId);

    verify(permissionGrants)
        .grantIfAbsent(masterUserId, PermissionCode.RESIDENT_MANAGE, approverId);
  }

  @Test
  void approve_whenPendingMember_activatesWithoutMastershipOrPermission() {
    UUID memberId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();

    User user = mock(User.class);
    when(user.getId()).thenReturn(memberId);
    when(user.isUnitMaster()).thenReturn(false);
    when(userRepo.findById(memberId)).thenReturn(Optional.of(user));

    service.approve(memberId, approverId);

    verify(user).approveAsMember(approverId);
    verify(user, never()).approveAsMaster(any());
    verify(permissionGrants, never()).grantIfAbsent(any(), any(), any());
    verify(unitRepo, never()).findById(any());
  }

  private RegisterMasterRequest baseReq() {
    return new RegisterMasterRequest(
        "Paulo", "Paulo", "paulo@x.com", "+5511999999999", "702C", "Senha@1234", "1.0.0", false);
  }

  private ConsentDocument newConsent(String v) {
    ConsentDocument c = newInstance(ConsentDocument.class);
    setField(c, "version", v);
    setField(c, "publishedAt", Instant.now());
    return c;
  }

  static <T> T newInstance(Class<T> clazz) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate " + clazz.getSimpleName(), e);
    }
  }

  static void setField(Object target, String name, Object value) {
    try {
      var f = findField(target.getClass(), name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static java.lang.reflect.Field findField(Class<?> c, String name) throws NoSuchFieldException {
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ex) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
