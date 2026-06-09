package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.feature.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Busca de usuários restrita ao contexto de gestão de acessos. */
public interface AccessUserRepository extends Repository<User, UUID> {

  @Query(
      """
      SELECT DISTINCT new br.com.condominio.feature.access.dto.UserSearchResult(
             u.id, u.fullName, un.code)
        FROM User u
        JOIN UserEmail ue ON ue.userId = u.id
        LEFT JOIN Unit un ON un.id = u.unitId
       WHERE u.status = br.com.condominio.feature.user.UserStatus.ACTIVE
         AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
              OR LOWER(ue.email) LIKE LOWER(CONCAT('%', :term, '%')))
       ORDER BY u.fullName
      """)
  List<UserSearchResult> search(@Param("term") String term, Pageable pageable);
}
