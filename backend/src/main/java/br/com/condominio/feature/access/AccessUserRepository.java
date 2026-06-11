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

  // Duas queries em vez de um guard ":term IS NULL": com termo nulo o Postgres não consegue
  // determinar o tipo do bind parameter no PREPARE (agravado por email citext) e estoura 500.
  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code, u.phone)
            FROM User u
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(u.id)
            FROM User u
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
          """)
  Page<UserSearchResult> findActivePageAll(Pageable pageable);

  @Query(
      value =
          """
          SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
                 u.id, u.fullName, un.code, u.phone)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(un.code) LIKE LOWER(CONCAT('%', :term, '%')))
           ORDER BY u.fullName
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT u.id)
            FROM User u
            LEFT JOIN UserEmail ue ON ue.userId = u.id
            LEFT JOIN Unit un ON un.id = u.unitId
           WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
             AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%'))
                  OR LOWER(un.code) LIKE LOWER(CONCAT('%', :term, '%')))
          """)
  Page<UserSearchResult> findActivePageByTerm(@Param("term") String term, Pageable pageable);
}
