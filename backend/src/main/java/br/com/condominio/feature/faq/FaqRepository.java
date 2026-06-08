package br.com.condominio.feature.faq;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

  List<Faq> findAllByPublishedTrueOrderByCategoryAscOrderingAsc();

  List<Faq> findAllByOrderByCategoryAscOrderingAsc();

  @Query("select max(f.ordering) from Faq f where f.category = :category")
  Integer findMaxOrderingByCategory(@Param("category") String category);
}
