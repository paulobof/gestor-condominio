package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
}
