package br.com.condominio.feature.faq;

import br.com.condominio.feature.faq.dto.CreateFaqRequest;
import br.com.condominio.feature.faq.dto.FaqView;
import br.com.condominio.feature.faq.dto.PublishFaqRequest;
import br.com.condominio.feature.faq.dto.ReorderFaqRequest;
import br.com.condominio.feature.faq.dto.UpdateFaqRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FAQ do condomínio. Leitura para qualquer autenticado; escrita só com {@code FAQ_MANAGE}. */
@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.faq.enabled", havingValue = "true")
public class FaqController {

  private final FaqService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<FaqView> listPublished() {
    return service.listPublished();
  }

  @GetMapping("/all")
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public List<FaqView> listAll() {
    return service.listAll();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public ResponseEntity<FaqView> create(@Valid @RequestBody CreateFaqRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  // /reorder MUST be declared before /{id} so "reorder" is not captured as a path variable
  @PutMapping("/reorder")
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderFaqRequest body) {
    service.reorder(body.items());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public FaqView update(@PathVariable UUID id, @Valid @RequestBody UpdateFaqRequest body) {
    return service.update(id, body);
  }

  @PutMapping("/{id}/publish")
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public FaqView publish(@PathVariable UUID id, @Valid @RequestBody PublishFaqRequest body) {
    return service.setPublished(id, body.published());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('FAQ_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
