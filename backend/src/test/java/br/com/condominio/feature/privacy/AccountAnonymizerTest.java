package br.com.condominio.feature.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.unit.Unit;
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
import org.springframework.test.util.ReflectionTestUtils;

class AccountAnonymizerTest {

  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private UnitRepository unitRepo;
  private RefreshTokenRepository refreshTokenRepo;
  private AccountAnonymizer anonymizer;

  private final UUID userId = UUID.randomUUID();
  private final UUID unitId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    unitRepo = mock(UnitRepository.class);
    refreshTokenRepo = mock(RefreshTokenRepository.class);
    anonymizer = new AccountAnonymizer(userRepo, emailRepo, unitRepo, refreshTokenRepo);
  }

  @Test
  void tiraPiiDoUsuarioEDosEmailsEEncerraSessoes() {
    User user = user(false);
    UserEmail primary = email("ana@example.com");
    UserEmail secondary = email("ana.trabalho@example.com");
    when(emailRepo.findByUserId(userId)).thenReturn(List.of(primary, secondary));

    anonymizer.anonymize(user, "inactivity");

    assertThat(user.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
    assertThat(user.getFullName()).isEqualTo("Usuário Removido");
    assertThat(user.getPhone()).isNull();
    assertThat(primary.getEmail()).doesNotContain("ana").endsWith("@anonimizado.invalid");
    assertThat(secondary.getEmail()).doesNotContain("ana").isNotEqualTo(primary.getEmail());
    verify(userRepo).save(user);
    verify(refreshTokenRepo).revokeAllByUserId(userId, "inactivity");
    verifyNoInteractions(unitRepo);
  }

  @Test
  void masterAnonimizadoLiberaAUnidade() {
    User user = user(true);
    Unit unit = newInstance(Unit.class);
    ReflectionTestUtils.setField(unit, "masterUserId", userId);
    when(unitRepo.findById(unitId)).thenReturn(Optional.of(unit));
    when(emailRepo.findByUserId(userId)).thenReturn(List.of());

    anonymizer.anonymize(user, "inactivity");

    assertThat(unit.getMasterUserId()).isNull();
    assertThat(user.isUnitMaster()).isFalse();
  }

  private User user(boolean master) {
    User u = newInstance(User.class);
    ReflectionTestUtils.setField(u, "id", userId);
    ReflectionTestUtils.setField(u, "unitId", unitId);
    ReflectionTestUtils.setField(u, "isUnitMaster", master);
    ReflectionTestUtils.setField(u, "status", UserStatus.ACTIVE);
    ReflectionTestUtils.setField(u, "fullName", "Ana Souza");
    ReflectionTestUtils.setField(u, "phone", "+5511999999999");
    ReflectionTestUtils.setField(u, "passwordHash", "hash");
    return u;
  }

  private UserEmail email(String address) {
    UserEmail e = UserEmail.primary(userId, address);
    ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
    return e;
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
