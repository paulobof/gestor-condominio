package br.com.condominio.feature.privacy;

import br.com.condominio.feature.privacy.event.UserAnonymizedEvent;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Apaga o comprovante de residência do MinIO após o commit da anonimização. Fora da transação
 * porque é uma operação externa (storage S3), per spec 4.5 e CLAUDE.md.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAnonymizedListener {

  private final FileStorage storage;
  private final MinioProperties props;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAnonymized(UserAnonymizedEvent event) {
    if (event.residenceProofObjectKey() == null) return;
    try {
      storage.delete(props.getBucketProofs(), event.residenceProofObjectKey());
      log.info("privacy.proof.purged userId={} bucket={}", event.userId(), props.getBucketProofs());
    } catch (Exception e) {
      log.error(
          "privacy.proof.purge.failed userId={} key={} msg={}",
          event.userId(),
          event.residenceProofObjectKey(),
          e.getMessage());
    }
  }
}
