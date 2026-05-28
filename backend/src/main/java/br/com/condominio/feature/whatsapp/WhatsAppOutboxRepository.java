package br.com.condominio.feature.whatsapp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppOutboxRepository extends JpaRepository<WhatsAppOutboxEntry, UUID> {

  /**
   * Entradas em PENDING ou FAILED ainda dentro do orçamento de tentativas (max_attempts). Usado
   * pelo WhatsAppRetryScheduler.
   */
  @Query(
      "SELECT e FROM WhatsAppOutboxEntry e "
          + "WHERE e.status IN ("
          + "  br.com.condominio.feature.whatsapp.WhatsAppOutboxEntry$Status.PENDING, "
          + "  br.com.condominio.feature.whatsapp.WhatsAppOutboxEntry$Status.FAILED) "
          + "  AND e.attempts < :maxAttempts "
          + "ORDER BY e.createdAt ASC")
  List<WhatsAppOutboxEntry> findRetryable(@Param("maxAttempts") int maxAttempts, Pageable pageable);

  /** Soft-delete em massa de entradas SENT mais antigas que cutoff (retenção 90 dias). */
  @Modifying
  @Query(
      "UPDATE WhatsAppOutboxEntry e SET e.deletedAt = :now "
          + "WHERE e.deletedAt IS NULL AND e.createdAt < :cutoff")
  int softDeleteOlderThan(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
