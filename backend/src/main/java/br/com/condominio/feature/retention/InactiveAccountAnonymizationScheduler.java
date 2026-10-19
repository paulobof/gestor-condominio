package br.com.condominio.feature.retention;

import br.com.condominio.feature.privacy.AccountAnonymizer;
import br.com.condominio.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Anonimiza contas de morador sem uso há 12 meses, como promete o termo de privacidade. "Uso" é
 * login ou renovação de sessão ({@code last_login_at}). Cada conta é anonimizada na sua própria
 * transação: uma falha não impede as demais.
 *
 * <p>Roda diariamente às 03:50.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InactiveAccountAnonymizationScheduler {

  static final long INACTIVITY_DAYS = 365;

  private final UserRepository userRepo;
  private final AccountAnonymizer anonymizer;
  private final Clock clock;

  @Scheduled(cron = "0 50 3 * * *")
  public void run() {
    Instant cutoff = clock.instant().minus(INACTIVITY_DAYS, ChronoUnit.DAYS);
    int done = 0;
    for (UUID id : userRepo.findInactiveAnonymizationCandidates(cutoff)) {
      try {
        var user = userRepo.findById(id);
        if (user.isPresent()) {
          anonymizer.anonymize(user.get(), "inactivity");
          done++;
        }
      } catch (RuntimeException e) {
        log.error("retention.inactive-anonymize falhou userId={}", id, e);
      }
    }
    if (done > 0) log.info("retention.inactive-anonymize anonymized={}", done);
  }
}
