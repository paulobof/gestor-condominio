package br.com.condominio.feature.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAssignmentLogTest {

  private static final UUID TARGET = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();

  @Test
  void assign_setsActionAndFields() {
    RoleAssignmentLog log = RoleAssignmentLog.assign(TARGET, (short) 99, ACTOR);

    assertThat(log.getAction()).isEqualTo("ASSIGN");
    assertThat(log.getTargetUserId()).isEqualTo(TARGET);
    assertThat(log.getRoleId()).isEqualTo((short) 99);
    assertThat(log.getActorUserId()).isEqualTo(ACTOR);
    assertThat(log.getCreatedAt()).isNotNull();
  }

  @Test
  void remove_setsActionRemove() {
    RoleAssignmentLog log = RoleAssignmentLog.remove(TARGET, (short) 2, ACTOR);

    assertThat(log.getAction()).isEqualTo("REMOVE");
    assertThat(log.getTargetUserId()).isEqualTo(TARGET);
    assertThat(log.getRoleId()).isEqualTo((short) 2);
    assertThat(log.getActorUserId()).isEqualTo(ACTOR);
    assertThat(log.getCreatedAt()).isNotNull();
  }
}
