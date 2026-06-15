package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void approveAsOwner_activatesPendingNonMaster() {
    User u = newPendingNonMaster();
    UUID approver = UUID.randomUUID();
    u.approveAsOwner(approver);
    assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(u.isUnitMaster()).isFalse();
  }

  @Test
  void approveAsOwner_whenNotPending_throws() {
    User u = newPendingNonMaster();
    u.approveAsOwner(UUID.randomUUID());
    assertThatThrownBy(() -> u.approveAsOwner(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Cria um User PENDING_APPROVAL não-master via reflection (factories de produção exigem proof).
   */
  private User newPendingNonMaster() {
    try {
      var ctor = User.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      User u = ctor.newInstance();
      var status = User.class.getDeclaredField("status");
      status.setAccessible(true);
      status.set(u, UserStatus.PENDING_APPROVAL);
      var master = User.class.getDeclaredField("isUnitMaster");
      master.setAccessible(true);
      master.set(u, false);
      return u;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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
