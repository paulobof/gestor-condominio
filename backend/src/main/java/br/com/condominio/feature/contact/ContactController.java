package br.com.condominio.feature.contact;

import br.com.condominio.feature.contact.dto.ContactView;
import br.com.condominio.feature.contact.dto.CreateContactRequest;
import br.com.condominio.feature.contact.dto.UpdateContactRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contatos úteis do condomínio. Leitura para qualquer autenticado; escrita só com {@code
 * CONTACT_MANAGE}.
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.contacts.enabled", havingValue = "true")
public class ContactController {

  private final ContactService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<ContactView> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('CONTACT_MANAGE')")
  public ResponseEntity<ContactView> create(@Valid @RequestBody CreateContactRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('CONTACT_MANAGE')")
  public ContactView update(@PathVariable UUID id, @Valid @RequestBody UpdateContactRequest body) {
    return service.update(id, body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('CONTACT_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
