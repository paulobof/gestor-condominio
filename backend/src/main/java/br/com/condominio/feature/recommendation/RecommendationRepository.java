package br.com.condominio.feature.recommendation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  List<Recommendation> findByResidentUserIdAndStatus(
      UUID residentUserId, RecommendationStatus status);

  // cast(:param as string): sem o cast, o Hibernate 6 vincula parâmetro String null como `bytea`
  // no Postgres, e `lower(bytea)` não existe (SQLState 42883). O cast fixa o tipo mesmo quando
  // null.
  @Query(
      """
      select distinct r from Recommendation r
      left join r.tags t
      where r.status = br.com.condominio.feature.recommendation.RecommendationStatus.ACTIVE
        and (:tag is null or lower(t.slug) = lower(cast(:tag as string)))
        and (:residentOnly = false or r.resident = true)
        and (:search is null
             or lower(r.serviceName) like lower(concat('%', cast(:search as string), '%'))
             or lower(r.professionalName) like lower(concat('%', cast(:search as string), '%')))
      order by r.resident desc, r.rating desc nulls last, r.createdAt desc
      """)
  Page<Recommendation> search(String tag, boolean residentOnly, String search, Pageable pageable);
}
