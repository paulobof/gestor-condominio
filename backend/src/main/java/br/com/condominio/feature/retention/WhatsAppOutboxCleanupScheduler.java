package br.com.condominio.feature.retention;

import br.com.condominio.feature.whatsapp.WhatsAppOutboxRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Soft-delete de entradas da outbox com mais de 90 dias. Roda diariamente às 03:40. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOutboxCleanupScheduler {

  private final WhatsAppOutboxRepository repo;

  @Scheduled(cron = "0 40 3 * * *")
  @Transactional
  public void purge() {
    Instant now = Instant.now();
    Instant cutoff = now.minus(90, ChronoUnit.DAYS);
    int n = repo.softDeleteOlderThan(cutoff, now);
    if (n > 0) log.info("outbox.cleanup soft-deleted={} cutoff={}", n, cutoff);
  }
}
