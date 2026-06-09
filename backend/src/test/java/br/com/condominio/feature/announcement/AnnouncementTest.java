package br.com.condominio.feature.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnnouncementTest {

  private final UUID author = UUID.randomUUID();

  @Test
  void create_setsFields() {
    Announcement a = Announcement.create(author, "Manutenção", "Água desligada 9h-12h", 0);

    assertThat(a.getAuthorUserId()).isEqualTo(author);
    assertThat(a.getTitle()).isEqualTo("Manutenção");
    assertThat(a.getBody()).isEqualTo("Água desligada 9h-12h");
    assertThat(a.getPosition()).isZero();
  }

  @Test
  void edit_updatesTitleAndBody() {
    Announcement a = Announcement.create(author, "Antigo", "corpo", 0);

    a.edit("Novo título", "novo corpo");

    assertThat(a.getTitle()).isEqualTo("Novo título");
    assertThat(a.getBody()).isEqualTo("novo corpo");
  }

  @Test
  void moveTo_changesPosition() {
    Announcement a = Announcement.create(author, "Aviso", "corpo", 0);

    a.moveTo(5);

    assertThat(a.getPosition()).isEqualTo(5);
  }
}
