package br.com.condominio.feature.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.password.event.PasswordResetCompletedEvent;
import br.com.condominio.feature.password.event.PasswordResetRequestedEvent;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetServiceTest {

  private UserRepository userRepo;
  private UserEmailRepository userEmailRepo;
  private PasswordResetTokenRepository tokenRepo;
  private PasswordHistoryRepository historyRepo;
  private RefreshTokenRepository refreshTokenRepo;
  private PasswordEncoder encoder;
  private PasswordResetProperties props;
  private ApplicationEventPublisher events;
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    userRepo = mock(UserRepository.class);
    userEmailRepo = mock(UserEmailRepository.class);
    tokenRepo = mock(PasswordResetTokenRepository.class);
    historyRepo = mock(PasswordHistoryRepository.class);
    refreshTokenRepo = mock(RefreshTokenRepository.class);
    encoder = mock(PasswordEncoder.class);
    props = new PasswordResetProperties();
    props.setTtl(Duration.ofMinutes(30));
    props.setBaseUrl("http://localhost:5173/reset");
    events = mock(ApplicationEventPublisher.class);
    service =
        new PasswordResetService(
            userRepo,
            userEmailRepo,
            tokenRepo,
            historyRepo,
            refreshTokenRepo,
            encoder,
            props,
            events);
    ReflectionTestUtils.setField(service, "pepperVersion", (short) 1);
  }

  // ============ requestReset ============

  @Test
  void requestResetIgnoraSeEmailNaoCadastrado() {
    when(userEmailRepo.findActiveByEmailIgnoreCase("absent@x.com")).thenReturn(Optional.empty());
    service.requestReset("absent@x.com", "1.2.3.4");
    verifyNoInteractions(events);
    verify(tokenRepo, never()).save(any());
  }

  @Test
  void requestResetIgnoraSeUserDisabled() {
    UserEmail ue = makeEmail();
    when(userEmailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.of(ue));
    User u = makeUser(ue.getUserId(), UserStatus.DISABLED, Instant.parse("2024-01-01T00:00:00Z"));
    when(userRepo.findById(ue.getUserId())).thenReturn(Optional.of(u));
    service.requestReset("x@x.com", "ip");
    verifyNoInteractions(events);
  }

  @Test
  void requestResetIgnoraSePhoneNaoVerificado() {
    UserEmail ue = makeEmail();
    when(userEmailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.of(ue));
    User u = makeUser(ue.getUserId(), UserStatus.ACTIVE, null);
    when(userRepo.findById(ue.getUserId())).thenReturn(Optional.of(u));
    service.requestReset("x@x.com", "ip");
    verifyNoInteractions(events);
  }

  @Test
  void requestResetUsuarioElegivelGeraTokenInvalidaAnterioresEPublicaEvento() {
    UserEmail ue = makeEmail();
    when(userEmailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.of(ue));
    User u = makeUser(ue.getUserId(), UserStatus.ACTIVE, Instant.parse("2024-01-01T00:00:00Z"));
    when(userRepo.findById(ue.getUserId())).thenReturn(Optional.of(u));
    when(tokenRepo.save(any()))
        .thenAnswer(
            inv -> {
              PasswordResetToken t = inv.getArgument(0);
              ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
              return t;
            });

    service.requestReset("x@x.com", "9.8.7.6");

    verify(tokenRepo).invalidateAllUserTokens(eq(ue.getUserId()), any(Instant.class));
    verify(tokenRepo).save(any(PasswordResetToken.class));
    verify(events).publishEvent(any(PasswordResetRequestedEvent.class));
  }

  // ============ consumeReset ============

  @Test
  void consumeResetTokenInexistenteLancaInvalido() {
    when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.consumeReset("raw", "Senha@1234", "ip"))
        .isInstanceOf(PasswordResetException.class)
        .extracting("code")
        .isEqualTo("INVALID_OR_EXPIRED_TOKEN");
  }

  @Test
  void consumeResetTokenExpiradoLancaInvalido() {
    PasswordResetToken token =
        PasswordResetToken.create(UUID.randomUUID(), "hash", Instant.now().minusSeconds(10), "ip");
    when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    assertThatThrownBy(() -> service.consumeReset("raw", "Senha@1234", "ip"))
        .isInstanceOf(PasswordResetException.class)
        .extracting("code")
        .isEqualTo("INVALID_OR_EXPIRED_TOKEN");
  }

  @Test
  void consumeResetTokenJaUsadoLancaInvalido() {
    PasswordResetToken token =
        PasswordResetToken.create(UUID.randomUUID(), "hash", Instant.now().plusSeconds(600), "ip");
    ReflectionTestUtils.setField(token, "usedAt", Instant.now().minusSeconds(60));
    when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    assertThatThrownBy(() -> service.consumeReset("raw", "Senha@1234", "ip"))
        .isInstanceOf(PasswordResetException.class)
        .extracting("code")
        .isEqualTo("INVALID_OR_EXPIRED_TOKEN");
  }

  @Test
  void consumeResetSenhaIgualUmaDasUltimas5LancaReuso() {
    UUID userId = UUID.randomUUID();
    PasswordResetToken token =
        PasswordResetToken.create(userId, "hash", Instant.now().plusSeconds(600), "ip");
    ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
    when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    User u = makeUser(userId, UserStatus.ACTIVE, Instant.now());
    when(userRepo.findById(userId)).thenReturn(Optional.of(u));
    PasswordHistory old = PasswordHistory.create(userId, "old-hash", (short) 1);
    when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(old));
    when(encoder.matches("Senha@1234", "old-hash")).thenReturn(true);

    assertThatThrownBy(() -> service.consumeReset("raw", "Senha@1234", "ip"))
        .isInstanceOf(PasswordResetException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_REUSED");
  }

  @Test
  void consumeResetSucessoMudaSenhaConsomeRevogaRefreshEPublicaEvento() {
    UUID userId = UUID.randomUUID();
    PasswordResetToken token =
        PasswordResetToken.create(userId, "hash", Instant.now().plusSeconds(600), "ip");
    ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
    when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    User u = makeUser(userId, UserStatus.ACTIVE, Instant.now());
    when(userRepo.findById(userId)).thenReturn(Optional.of(u));
    when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
    when(encoder.encode("Senha@1234")).thenReturn("new-hash");

    service.consumeReset("raw", "Senha@1234", "ip");

    assertThat(u.getPasswordHash()).isEqualTo("new-hash");
    assertThat(token.getUsedAt()).isNotNull();
    verify(historyRepo).save(any(PasswordHistory.class));
    verify(refreshTokenRepo).revokeAllByUserId(eq(userId), eq("password_reset"));
    verify(events).publishEvent(any(PasswordResetCompletedEvent.class));
  }

  // ============ helpers ============

  private UserEmail makeEmail() {
    UserEmail ue = newInstance(UserEmail.class);
    ReflectionTestUtils.setField(ue, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(ue, "userId", UUID.randomUUID());
    ReflectionTestUtils.setField(ue, "email", "x@x.com");
    return ue;
  }

  private User makeUser(UUID id, UserStatus status, Instant phoneVerifiedAt) {
    User u = newInstance(User.class);
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "status", status);
    ReflectionTestUtils.setField(u, "phoneVerifiedAt", phoneVerifiedAt);
    ReflectionTestUtils.setField(u, "phone", "+5511999999999");
    ReflectionTestUtils.setField(u, "greetingName", "Tester");
    ReflectionTestUtils.setField(u, "passwordHash", "current-hash");
    ReflectionTestUtils.setField(u, "passwordPepperVersion", (short) 1);
    return u;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var ctor = type.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed instantiating " + type, e);
    }
  }
}
