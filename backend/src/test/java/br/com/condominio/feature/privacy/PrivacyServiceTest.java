package br.com.condominio.feature.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.privacy.event.UserAnonymizedEvent;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PrivacyServiceTest {

  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private UserRoleRepository userRoleRepo;
  private RoleRepository roleRepo;
  private UnitRepository unitRepo;
  private RefreshTokenRepository refreshTokenRepo;
  private ProcessingActivitiesProvider activitiesProvider;
  private PasswordEncoder encoder;
  private ApplicationEventPublisher events;
  private PrivacyService service;

  @BeforeEach
  void setUp() {
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    userRoleRepo = mock(UserRoleRepository.class);
    roleRepo = mock(RoleRepository.class);
    unitRepo = mock(UnitRepository.class);
    refreshTokenRepo = mock(RefreshTokenRepository.class);
    activitiesProvider = mock(ProcessingActivitiesProvider.class);
    encoder = mock(PasswordEncoder.class);
    events = mock(ApplicationEventPublisher.class);
    service =
        new PrivacyService(
            userRepo,
            emailRepo,
            userRoleRepo,
            roleRepo,
            unitRepo,
            refreshTokenRepo,
            activitiesProvider,
            encoder,
            events);
  }

  @Test
  void exportSelfRetornaDadosCompletosDoTitular() {
    UUID userId = UUID.randomUUID();
    User u = makeUser(userId);
    when(userRepo.findById(userId)).thenReturn(Optional.of(u));
    UserEmail ue = newInstance(UserEmail.class);
    ReflectionTestUtils.setField(ue, "userId", userId);
    ReflectionTestUtils.setField(ue, "email", "test@x.com");
    when(emailRepo.findByUserId(userId)).thenReturn(List.of(ue));
    when(userRoleRepo.findById_UserId(userId)).thenReturn(List.of());

    var resp = service.exportSelf(userId);

    assertThat(resp.userId()).isEqualTo(userId);
    assertThat(resp.fullName()).isEqualTo("Tester");
    assertThat(resp.emails()).containsExactly("test@x.com");
    assertThat(resp.status()).isEqualTo("ACTIVE");
    assertThat(resp.exportedAt()).isNotNull();
  }

  @Test
  void exportSelfUsuarioInexistenteLancaUserNotFound() {
    UUID id = UUID.randomUUID();
    when(userRepo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.exportSelf(id))
        .isInstanceOf(PrivacyException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_FOUND");
  }

  @Test
  void updateWhatsappOptInSalvaUserComNovoValor() {
    UUID id = UUID.randomUUID();
    User u = makeUser(id);
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    service.updateWhatsappOptIn(id, true);
    assertThat(u.isWhatsappOptIn()).isTrue();
    verify(userRepo).save(u);
  }

  @Test
  void anonymizeSelfSenhaErradaLancaInvalidPassword() {
    UUID id = UUID.randomUUID();
    User u = makeUser(id);
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    when(encoder.matches("wrong", "current-hash")).thenReturn(false);
    assertThatThrownBy(() -> service.anonymizeSelf(id, "wrong"))
        .isInstanceOf(PrivacyException.class)
        .extracting("code")
        .isEqualTo("INVALID_PASSWORD");
    verifyNoInteractions(events);
  }

  @Test
  void anonymizeSelfSucessoMudaStatusRevogaRefreshEPublicaEvento() {
    UUID id = UUID.randomUUID();
    User u = makeUser(id);
    ReflectionTestUtils.setField(u, "residenceProofObjectKey", "some-key");
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    when(encoder.matches("Senha@1234", "current-hash")).thenReturn(true);

    service.anonymizeSelf(id, "Senha@1234");

    assertThat(u.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
    assertThat(u.getFullName()).isEqualTo("Usuário Removido");
    assertThat(u.getResidenceProofObjectKey()).isNull();
    verify(refreshTokenRepo).revokeAllByUserId(eq(id), eq("self_anonymize"));
    verify(events).publishEvent(any(UserAnonymizedEvent.class));
  }

  // ============ helpers ============

  private User makeUser(UUID id) {
    User u = newInstance(User.class);
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "status", UserStatus.ACTIVE);
    ReflectionTestUtils.setField(u, "fullName", "Tester");
    ReflectionTestUtils.setField(u, "greetingName", "Tester");
    ReflectionTestUtils.setField(u, "phone", "+5511999999999");
    ReflectionTestUtils.setField(u, "passwordHash", "current-hash");
    ReflectionTestUtils.setField(u, "passwordPepperVersion", (short) 1);
    return u;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var c = type.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
