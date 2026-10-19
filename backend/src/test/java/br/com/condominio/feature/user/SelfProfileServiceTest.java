package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.password.PasswordHistory;
import br.com.condominio.feature.password.PasswordHistoryRepository;
import br.com.condominio.feature.user.dto.OwnProfileView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class SelfProfileServiceTest {

  private static final short PEPPER_VERSION = 3;

  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private PasswordHistoryRepository historyRepo;
  private PasswordEncoder encoder;
  private SelfProfileService service;
  private UUID userId;
  private User user;

  @BeforeEach
  void setUp() {
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    historyRepo = mock(PasswordHistoryRepository.class);
    encoder = mock(PasswordEncoder.class);
    service = new SelfProfileService(userRepo, emailRepo, historyRepo, encoder);
    ReflectionTestUtils.setField(service, "pepperVersion", PEPPER_VERSION);

    userId = UUID.randomUUID();
    user = User.newActiveByAdmin(null, "Ana Silva", "+5511999999999", "OLD_HASH", (short) 1);
    ReflectionTestUtils.setField(user, "id", userId);
    ReflectionTestUtils.setField(user, "greetingName", "Ana");
  }

  private void withPrimaryEmail(String email) {
    when(emailRepo.findPrimaryByUserId(userId))
        .thenReturn(Optional.of(UserEmail.primary(userId, email)));
  }

  @Test
  void getProfile_retornaDadosDaContaComEmailPrimario() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    withPrimaryEmail("ana@x.com");

    OwnProfileView view = service.getProfile(userId);

    assertThat(view.fullName()).isEqualTo("Ana Silva");
    assertThat(view.greetingName()).isEqualTo("Ana");
    assertThat(view.phone()).isEqualTo("+5511999999999");
    assertThat(view.email()).isEqualTo("ana@x.com");
  }

  @Test
  void getProfile_usuarioInexistente_lancaUserNotFound() {
    when(userRepo.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProfile(userId))
        .isInstanceOf(SelfProfileException.class)
        .extracting("code")
        .isEqualTo("USER_NOT_FOUND");
  }

  @Test
  void updateProfile_aplicaNoDominioESalva() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    withPrimaryEmail("ana@x.com");

    OwnProfileView view = service.updateProfile(userId, " Ana Maria ", "", "+5511888888888");

    assertThat(user.getFullName()).isEqualTo("Ana Maria");
    assertThat(user.getGreetingName()).isNull();
    assertThat(user.getPhone()).isEqualTo("+5511888888888");
    verify(userRepo).save(user);
    assertThat(view.fullName()).isEqualTo("Ana Maria");
    assertThat(view.email()).isEqualTo("ana@x.com");
  }

  @Test
  void changePassword_senhaAtualErrada_lancaInvalidPassword_semAlterar() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(encoder.matches("errada", "OLD_HASH")).thenReturn(false);

    assertThatThrownBy(() -> service.changePassword(userId, "errada", "Nova@Senha123"))
        .isInstanceOf(SelfProfileException.class)
        .extracting("code")
        .isEqualTo("INVALID_PASSWORD");
    assertThat(user.getPasswordHash()).isEqualTo("OLD_HASH");
    verify(userRepo, never()).save(any());
    verify(historyRepo, never()).save(any());
  }

  @Test
  void changePassword_senhaUsadaRecentemente_lancaPasswordReused() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(encoder.matches("Atual@123", "OLD_HASH")).thenReturn(true);
    PasswordHistory old = PasswordHistory.create(userId, "HIST_HASH", (short) 1);
    when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(old));
    when(encoder.matches("Antiga@123", "HIST_HASH")).thenReturn(true);

    assertThatThrownBy(() -> service.changePassword(userId, "Atual@123", "Antiga@123"))
        .isInstanceOf(SelfProfileException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_REUSED");
    verify(userRepo, never()).save(any());
  }

  @Test
  void changePassword_novaIgualAAtual_lancaPasswordReused() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(encoder.matches("Atual@123", "OLD_HASH")).thenReturn(true);
    when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.changePassword(userId, "Atual@123", "Atual@123"))
        .isInstanceOf(SelfProfileException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_REUSED");
  }

  @Test
  void changePassword_sucesso_trocaHashComPepperERegistraHistorico() {
    ReflectionTestUtils.setField(user, "mustChangePassword", true);
    when(userRepo.findById(userId)).thenReturn(Optional.of(user));
    when(encoder.matches("Atual@123", "OLD_HASH")).thenReturn(true);
    when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
    when(encoder.encode("Nova@Senha123")).thenReturn("NEW_HASH");

    service.changePassword(userId, "Atual@123", "Nova@Senha123");

    assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
    assertThat(user.getPasswordPepperVersion()).isEqualTo(PEPPER_VERSION);
    assertThat(user.isMustChangePassword()).isFalse();
    verify(userRepo).save(user);
    ArgumentCaptor<PasswordHistory> captor = ArgumentCaptor.forClass(PasswordHistory.class);
    verify(historyRepo).save(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("NEW_HASH");
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
  }
}
