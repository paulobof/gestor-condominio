package br.com.condominio.feature.retention;

import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MinioProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LGPD: purga o comprovante de residência do MinIO {@code APP_PROOF_RETENTION_DAYS} (180 default)
 * dias após {@code approvedAt}. Limpa os campos {@code residence_proof_*} do user mas preserva o
 * histórico (não anonimiza).
 *
 * <p>Roda diariamente às 03:30 (timezone do servidor).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProofRetentionScheduler {

  private final UserRepository userRepo;
  private final FileStorage storage;
  private final MinioProperties props;

  @Value("${app.proof-retention-days:180}")
  private int retentionDays;

  @Scheduled(cron = "0 30 3 * * *")
  @Transactional
  public void purgeOldProofs() {
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    List<User> targets = userRepo.findApprovedWithProofBefore(cutoff);
    if (targets.isEmpty()) return;
    log.info("proof.retention scan: {} candidatos a purga (cutoff={})", targets.size(), cutoff);
    int purged = 0;
    for (User user : targets) {
      String key = user.getResidenceProofObjectKey();
      try {
        storage.delete(props.getBucketProofs(), key);
        clearProofFields(user);
        userRepo.save(user);
        purged++;
      } catch (Exception e) {
        log.warn(
            "proof.retention skip userId={} key={} reason={}", user.getId(), key, e.getMessage());
      }
    }
    log.info("proof.retention purged={} of {}", purged, targets.size());
  }

  private static void clearProofFields(User user) {
    try {
      setField(user, "residenceProofObjectKey", null);
      setField(user, "residenceProofFilename", null);
      setField(user, "residenceProofContentType", null);
      // proofVerifiedAt preservado para historicidade do fluxo
    } catch (Exception e) {
      throw new IllegalStateException("falha limpando campos de comprovante", e);
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    var f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
