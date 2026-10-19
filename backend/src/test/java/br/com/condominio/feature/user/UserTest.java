package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void updateProfile_setsAllEditableFields() {
    User u = User.newActiveByAdmin(null, "Antigo Nome", "+5511000000000", "HASH", (short) 1);
    UUID unit = UUID.randomUUID();

    u.updateProfile(
        "Novo Nome", "Novo", "+5511999999999", unit, Gender.FEMALE, LocalDate.of(1990, 1, 2));

    assertThat(u.getFullName()).isEqualTo("Novo Nome");
    assertThat(u.getGreetingName()).isEqualTo("Novo");
    assertThat(u.getPhone()).isEqualTo("+5511999999999");
    assertThat(u.getUnitId()).isEqualTo(unit);
    assertThat(u.getGender()).isEqualTo(Gender.FEMALE);
    assertThat(u.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
  }

  @Test
  void recordLogin_gravaPrimeiroAcessoEDepoisSoUmaVezPorDia() {
    User u = User.newActiveByAdmin(null, "Ana", null, "HASH", (short) 1);
    Instant t0 = Instant.parse("2026-10-19T10:00:00Z");

    u.recordLogin(t0);
    assertThat(u.getLastLoginAt()).isEqualTo(t0);

    u.recordLogin(t0.plus(2, ChronoUnit.HOURS));
    assertThat(u.getLastLoginAt()).isEqualTo(t0);

    Instant nextDay = t0.plus(25, ChronoUnit.HOURS);
    u.recordLogin(nextDay);
    assertThat(u.getLastLoginAt()).isEqualTo(nextDay);
  }

  @Test
  void anonymize_deixaDeSerMaster() {
    User u = User.newActiveByAdmin(UUID.randomUUID(), "Ana", "+5511999999999", "HASH", (short) 1);
    org.springframework.test.util.ReflectionTestUtils.setField(u, "isUnitMaster", true);

    u.anonymize();

    assertThat(u.isUnitMaster()).isFalse();
    assertThat(u.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
  }
}
