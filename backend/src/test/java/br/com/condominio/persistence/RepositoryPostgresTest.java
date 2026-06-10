package br.com.condominio.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.condominio.feature.recommendation.RecommendationRepository;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testes de integração contra Postgres real (Testcontainers, Flyway aplica o schema). Pegam bugs de
 * SQL/HQL que os testes unitários com mock de repositório não capturam — exatamente os encontrados
 * no e2e em HML: {@code lower(bytea)} com parâmetro null, e {@code @SQLDelete} de entidade
 * {@code @Version} sem {@code AND version = ?}. Pula automaticamente onde não há Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RepositoryPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
    // Flyway é a autoridade do schema; não deixar o Hibernate validar/criar no teste.
    r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }

  @Autowired private RecommendationRepository recommendations;
  @Autowired private UnitRepository units;
  @Autowired private br.com.condominio.feature.access.AccessUserRepository accessUsers;

  @Test
  void recommendationSearch_nullFilters_runsAgainstPostgres() {
    // Regressão (#19): lower(:tag)/lower(:search) com parâmetro null gerava lower(bytea)
    // (SQLState 42883) -> 500. Sem dados seedados, a lista é vazia, mas a query precisa EXECUTAR.
    assertThatCode(() -> recommendations.search(null, false, null, PageRequest.of(0, 10)))
        .doesNotThrowAnyException();
    assertThat(recommendations.search(null, false, null, PageRequest.of(0, 10)).getContent())
        .isEmpty();
  }

  @Test
  void recommendationSearch_allFilters_runsAgainstPostgres() {
    // Exercita os caminhos lower()/cast com tag, residentOnly e search preenchidos.
    assertThatCode(() -> recommendations.search("encanador", true, "enc", PageRequest.of(0, 10)))
        .doesNotThrowAnyException();
  }

  @Test
  void findActivePage_nullAndNonNullTerm_runsAgainstPostgres() {
    // Pega bugs de HQL: ":term IS NULL", DISTINCT + countQuery, LEFT JOIN.
    assertThatCode(() -> accessUsers.findActivePage(null, PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThatCode(() -> accessUsers.findActivePage("ana", PageRequest.of(0, 20)))
        .doesNotThrowAnyException();
    assertThat(accessUsers.findActivePage(null, PageRequest.of(0, 20))).isNotNull();
  }

  @Test
  void softDelete_versionedEntity_succeeds() {
    // Regressão (#20): @SQLDelete de entidade @Version precisa de "AND version = ?", senão o
    // Hibernate tenta vincular a version a um placeholder inexistente ("column index out of
    // range: 2"). Usa uma unidade seedada (V6).
    Unit unit = units.findAll().get(0);

    assertThatCode(() -> units.delete(unit)).doesNotThrowAnyException();
    units.flush();

    assertThat(units.findById(unit.getId())).isEmpty(); // @SQLRestriction filtra soft-deletados
  }
}
