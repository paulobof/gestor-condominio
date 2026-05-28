package br.com.condominio.feature.retention;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-delete de refresh tokens revogados ou expirados há mais de 90 dias. Esses registros não têm
 * valor de auditoria (auditoria fica em sensitive_access_log).
 *
 * <p>Roda diariamente às 03:35.
 */
@Component
@Slf4j
public class RefreshTokenCleanupScheduler {

  @PersistenceContext private EntityManager em;

  @Scheduled(cron = "0 35 3 * * *")
  @Transactional
  public void purge() {
    Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
    int deleted =
        em.createNativeQuery(
                "DELETE FROM refresh_token "
                    + "WHERE (revoked = true AND revoked_at < :cutoff) "
                    + "   OR (expires_at < :cutoff)")
            .setParameter("cutoff", cutoff)
            .executeUpdate();
    if (deleted > 0) log.info("refresh.cleanup hard-deleted={}", deleted);
  }
}
