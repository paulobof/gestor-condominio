package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Listagem de usuários ativos restrita ao contexto de gestão de acessos. */
public interface AccessUserRepository extends Repository<User, UUID> {

  // :term nunca é passado diretamente a LOWER(); é só concatenado via CONCAT('%', :term, '%'),
  // e o guard ":term IS NULL" curto-circuita antes. Por isso não precisa de cast(:term as string)
  // (diferente de RecommendationRepository, que aplica LOWER(:param) direto).
  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (:term IS NULL
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT u.id)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (:term IS NULL
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
          """)
  Page<UserSearchResult> findActivePage(@Param("term") String term, Pageable pageable);
}
