package br.com.condominio.feature.contact;

import br.com.condominio.feature.contact.dto.ContactView;
import br.com.condominio.feature.contact.dto.CreateContactRequest;
import br.com.condominio.feature.contact.dto.OpeningHoursDto;
import br.com.condominio.feature.contact.dto.UpdateContactRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactService {

  private final ContactRepository repo;
  private final ContactOpeningHoursRepository hoursRepo;

  @Transactional(readOnly = true)
  public List<ContactView> list() {
    return repo.findAllByOrderByCategoryAscNameAsc().stream().map(this::toView).toList();
  }

  @Transactional
  public ContactView create(CreateContactRequest b) {
    Contact c = repo.save(Contact.create(b.name(), b.category(), b.phone(), b.notes(), b.is24h()));
    saveHours(c.getId(), b.openingHours());
    return toView(c);
  }

  @Transactional
  public ContactView update(UUID id, UpdateContactRequest b) {
    Contact c = find(id);
    c.edit(b.name(), b.category(), b.phone(), b.notes(), b.is24h());
    repo.save(c);
    hoursRepo.deleteByOwnerId(id);
    saveHours(id, b.openingHours());
    return toView(c);
  }

  @Transactional
  public void delete(UUID id) {
    repo.delete(find(id));
  }

  private void saveHours(UUID ownerId, List<OpeningHoursDto> hours) {
    if (hours == null) return;
    for (OpeningHoursDto h : hours) {
      hoursRepo.save(
          ContactOpeningHours.create(
              ownerId, (short) h.dayOfWeek(), h.opensAt(), h.closesAt(), h.notes()));
    }
  }

  private ContactView toView(Contact c) {
    List<OpeningHoursDto> hours =
        hoursRepo.findByOwnerIdOrderByDayOfWeekAsc(c.getId()).stream()
            .map(
                h ->
                    new OpeningHoursDto(
                        h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.getNotes()))
            .toList();
    return new ContactView(
        c.getId(),
        c.getName(),
        c.getCategory(),
        c.getPhone(),
        c.getNotes(),
        c.is24h(),
        hours,
        c.getUpdatedAt());
  }

  private Contact find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ContactException("NOT_FOUND", "Contato não encontrado."));
  }
}
