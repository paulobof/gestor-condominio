package br.com.condominio.feature.recommendation;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  // cast(:param as string): sem o cast, o Hibernate 6 vincula parâmetro String null como `bytea`
  // no Postgres, e `lower(bytea)` não existe (SQLState 42883). O cast fixa o tipo mesmo quando
  // null.
  //
  // Filtro de tag por `exists` (subconsulta), não por `left join` + `distinct`: o `distinct` chega
  // ao SQL e o Postgres recusa `SELECT DISTINCT` com `ORDER BY` por expressão fora da lista de
  // seleção ("for SELECT DISTINCT, ORDER BY expressions must appear in select list") — e o score
  // bayesiano do `order by` é exatamente uma expressão dessas. Pôr o score na projeção resolveria o
  // erro mas devolveria Object[], quebrando o Page<Recommendation>. O `exists` também evita
  // multiplicar linhas por tag, então nem precisa de `distinct`.
  @Query(
      """
      select r from Recommendation r
      where r.status = br.com.condominio.feature.recommendation.RecommendationStatus.ACTIVE
        and (:tag is null or exists (
              select 1 from r.tags t where lower(t.slug) = lower(cast(:tag as string))))
        and (:search is null
             or lower(r.serviceName) like lower(concat('%', cast(:search as string), '%'))
             or lower(r.professionalName) like lower(concat('%', cast(:search as string), '%')))
      order by r.resident desc,
               (r.ratingSum + 10.5) / (r.reviewCount + 3) desc,
               r.createdAt desc
      """)
  Page<Recommendation> search(String tag, String search, Pageable pageable);
}
