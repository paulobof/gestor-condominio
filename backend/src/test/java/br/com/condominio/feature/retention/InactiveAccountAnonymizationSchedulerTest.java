package br.com.condominio.feature.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.privacy.AccountAnonymizer;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InactiveAccountAnonymizationSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-10-19T06:50:00Z");

  private final UserRepository userRepo = mock(UserRepository.class);
  private final AccountAnonymizer anonymizer = mock(AccountAnonymizer.class);
  private final InactiveAccountAnonymizationScheduler scheduler =
      new InactiveAccountAnonymizationScheduler(
          userRepo, anonymizer, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void buscaContasSemUsoHa12MesesEAnonimizaCadaUma() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    User ua = mock(User.class);
    User ub = mock(User.class);
    when(userRepo.findInactiveAnonymizationCandidates(any())).thenReturn(List.of(a, b));
    when(userRepo.findById(a)).thenReturn(Optional.of(ua));
    when(userRepo.findById(b)).thenReturn(Optional.of(ub));

    scheduler.run();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(userRepo).findInactiveAnonymizationCandidates(cutoff.capture());
    assertThat(cutoff.getValue()).isEqualTo(NOW.minus(365, ChronoUnit.DAYS));
    verify(anonymizer).anonymize(ua, "inactivity");
    verify(anonymizer).anonymize(ub, "inactivity");
  }

  @Test
  void falhaEmUmaContaNaoImpedeAsOutras() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    User ua = mock(User.class);
    User ub = mock(User.class);
    when(userRepo.findInactiveAnonymizationCandidates(any())).thenReturn(List.of(a, b));
    when(userRepo.findById(a)).thenReturn(Optional.of(ua));
    when(userRepo.findById(b)).thenReturn(Optional.of(ub));
    doThrow(new IllegalStateException("boom")).when(anonymizer).anonymize(eq(ua), any());

    scheduler.run();

    verify(anonymizer).anonymize(ub, "inactivity");
  }
}
