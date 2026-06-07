package br.com.condominio.feature.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnnouncementTest {

  private final UUID author = UUID.randomUUID();

  @Test
  void create_setsFields() {
    Announcement a = Announcement.create(author, "Manutenção", "Água desligada 9h-12h", true);

    assertThat(a.getAuthorUserId()).isEqualTo(author);
    assertThat(a.getTitle()).isEqualTo("Manutenção");
    assertThat(a.getBody()).isEqualTo("Água desligada 9h-12h");
    assertThat(a.isPinned()).isTrue();
  }

  @Test
  void edit_updatesFieldsIncludingPin() {
    Announcement a = Announcement.create(author, "Antigo", "corpo", true);

    a.edit("Novo título", "novo corpo", false);

    assertThat(a.getTitle()).isEqualTo("Novo título");
    assertThat(a.getBody()).isEqualTo("novo corpo");
    assertThat(a.isPinned()).isFalse();
  }
}
