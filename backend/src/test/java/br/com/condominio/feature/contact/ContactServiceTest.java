package br.com.condominio.feature.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.contact.dto.ContactView;
import br.com.condominio.feature.contact.dto.CreateContactRequest;
import br.com.condominio.feature.contact.dto.OpeningHoursDto;
import br.com.condominio.feature.contact.dto.UpdateContactRequest;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContactServiceTest {

  private ContactRepository repo;
  private ContactOpeningHoursRepository hoursRepo;
  private ContactService service;

  @BeforeEach
  void setUp() {
    repo = mock(ContactRepository.class);
    hoursRepo = mock(ContactOpeningHoursRepository.class);
    service = new ContactService(repo, hoursRepo);
    when(repo.save(any(Contact.class)))
        .thenAnswer(
            i -> {
              Contact c = i.getArgument(0);
              if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
              }
              return c;
            });
    when(hoursRepo.save(any(ContactOpeningHours.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Contact persisted(UUID id, String name, String category, String phone, boolean is24h) {
    Contact c = Contact.create(name, category, phone, null, is24h);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  // --- list ---

  @Test
  void list_returnsContactViewPerContact_withOpeningHoursLoaded() {
    UUID id = UUID.randomUUID();
    Contact c = persisted(id, "Portaria", "Segurança", "1100001111", false);
    ContactOpeningHours h =
        ContactOpeningHours.create(
            id, (short) 1, LocalTime.of(8, 0), LocalTime.of(18, 0), "segunda");
    ReflectionTestUtils.setField(h, "id", UUID.randomUUID());

    when(repo.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(c));
    when(hoursRepo.findByOwnerIdOrderByDayOfWeekAsc(id)).thenReturn(List.of(h));

    List<ContactView> views = service.list();

    assertThat(views).hasSize(1);
    ContactView v = views.get(0);
    assertThat(v.id()).isEqualTo(id);
    assertThat(v.name()).isEqualTo("Portaria");
    assertThat(v.category()).isEqualTo("Segurança");
    assertThat(v.phone()).isEqualTo("1100001111");
    assertThat(v.is24h()).isFalse();
    assertThat(v.openingHours()).hasSize(1);
    assertThat(v.openingHours().get(0).dayOfWeek()).isEqualTo(1);
    assertThat(v.openingHours().get(0).opensAt()).isEqualTo(LocalTime.of(8, 0));
    assertThat(v.openingHours().get(0).closesAt()).isEqualTo(LocalTime.of(18, 0));
    assertThat(v.openingHours().get(0).notes()).isEqualTo("segunda");
  }

  @Test
  void list_returnsEmptyWhenNoContacts() {
    when(repo.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of());

    List<ContactView> views = service.list();

    assertThat(views).isEmpty();
  }

  // --- create ---

  @Test
  void create_savesContactAndReturnsView() {
    CreateContactRequest req =
        new CreateContactRequest("Bombeiros", "Emergências", "193", null, false, List.of());

    ContactView view = service.create(req);

    assertThat(view.name()).isEqualTo("Bombeiros");
    assertThat(view.category()).isEqualTo("Emergências");
    assertThat(view.phone()).isEqualTo("193");
    assertThat(view.is24h()).isFalse();
    verify(repo).save(any(Contact.class));
  }

  @Test
  void create_savesOneOpeningHoursPerDto_withCorrectOwnerId() {
    UUID contactId = UUID.randomUUID();
    when(repo.save(any(Contact.class)))
        .thenAnswer(
            i -> {
              Contact c = i.getArgument(0);
              ReflectionTestUtils.setField(c, "id", contactId);
              return c;
            });

    OpeningHoursDto dto1 = new OpeningHoursDto(1, LocalTime.of(9, 0), LocalTime.of(17, 0), null);
    OpeningHoursDto dto2 = new OpeningHoursDto(2, LocalTime.of(9, 0), LocalTime.of(17, 0), null);
    CreateContactRequest req =
        new CreateContactRequest("Manutenção", "Serviços", "110", null, false, List.of(dto1, dto2));

    service.create(req);

    // two hours rows saved
    verify(hoursRepo, times(2)).save(any(ContactOpeningHours.class));
    // verify ownerId on each saved hours instance
    org.mockito.ArgumentCaptor<ContactOpeningHours> captor =
        org.mockito.ArgumentCaptor.forClass(ContactOpeningHours.class);
    verify(hoursRepo, times(2)).save(captor.capture());
    captor.getAllValues().forEach(h -> assertThat(h.getOwnerId()).isEqualTo(contactId));
  }

  @Test
  void create_withNullOpeningHours_savesNoHoursRows() {
    CreateContactRequest req =
        new CreateContactRequest("SAMU", "Emergências", "192", null, true, null);

    service.create(req);

    verify(hoursRepo, times(0)).save(any(ContactOpeningHours.class));
  }

  // --- update ---

  @Test
  void update_editsContactFieldsAndReplacesHours() {
    UUID id = UUID.randomUUID();
    Contact existing = persisted(id, "Velha", "Cat", "000", false);
    when(repo.findById(id)).thenReturn(Optional.of(existing));
    when(hoursRepo.findByOwnerIdOrderByDayOfWeekAsc(id)).thenReturn(List.of());

    OpeningHoursDto dto =
        new OpeningHoursDto(3, LocalTime.of(10, 0), LocalTime.of(20, 0), "quarta");
    UpdateContactRequest req =
        new UpdateContactRequest("Nova", "Nova Cat", "999", "obs", true, List.of(dto));

    ContactView view = service.update(id, req);

    assertThat(view.name()).isEqualTo("Nova");
    assertThat(view.category()).isEqualTo("Nova Cat");
    assertThat(view.phone()).isEqualTo("999");
    assertThat(view.notes()).isEqualTo("obs");
    assertThat(view.is24h()).isTrue();

    verify(hoursRepo).deleteByOwnerId(id);
    verify(hoursRepo, times(1)).save(any(ContactOpeningHours.class));
  }

  @Test
  void update_notFound_throwsContactExceptionWithNotFoundCode() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    UpdateContactRequest req = new UpdateContactRequest("N", "C", "P", null, false, List.of());

    assertThatThrownBy(() -> service.update(id, req))
        .isInstanceOf(ContactException.class)
        .satisfies(ex -> assertThat(((ContactException) ex).getCode()).isEqualTo("NOT_FOUND"))
        .hasMessageContaining("não encontrado");
  }

  // --- delete ---

  @Test
  void delete_callsRepositoryDeleteWithContact() {
    UUID id = UUID.randomUUID();
    Contact c = persisted(id, "Portaria", "Segurança", "111", false);
    when(repo.findById(id)).thenReturn(Optional.of(c));

    service.delete(id);

    verify(repo).delete(c);
  }

  @Test
  void delete_notFound_throwsContactExceptionWithNotFoundCode() {
    UUID id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(ContactException.class)
        .satisfies(ex -> assertThat(((ContactException) ex).getCode()).isEqualTo("NOT_FOUND"));
  }
}
