package br.com.condominio.feature.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  /** Lista para a tela: mais recentes primeiro. {@code @SQLRestriction} filtra soft-deletados. */
  List<Document> findAllByOrderByCreatedAtDesc();
}
