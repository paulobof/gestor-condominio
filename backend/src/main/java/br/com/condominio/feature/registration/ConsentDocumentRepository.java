package br.com.condominio.feature.registration;

import br.com.condominio.feature.consent.ConsentDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsentDocumentRepository extends JpaRepository<ConsentDocument, UUID> {
  Optional<ConsentDocument> findByVersion(String version);

  @Query("SELECT c FROM ConsentDocument c ORDER BY c.publishedAt DESC LIMIT 1")
  Optional<ConsentDocument> findLatest();
}
