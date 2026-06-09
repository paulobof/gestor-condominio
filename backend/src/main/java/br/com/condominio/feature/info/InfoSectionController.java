package br.com.condominio.feature.info;

import br.com.condominio.feature.info.dto.CreateInfoSectionRequest;
import br.com.condominio.feature.info.dto.InfoSectionView;
import br.com.condominio.feature.info.dto.ReorderInfoRequest;
import br.com.condominio.feature.info.dto.UpdateInfoSectionRequest;
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
 * Informações gerais do condomínio. Leitura para qualquer autenticado; escrita só com {@code
 * INFO_MANAGE} (síndico).
 */
@RestController
@RequestMapping("/api/info-sections")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.generalinfo.enabled", havingValue = "true")
public class InfoSectionController {

  private final InfoSectionService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<InfoSectionView> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<InfoSectionView> create(@Valid @RequestBody CreateInfoSectionRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  // /reorder ANTES de /{id} para "reorder" não ser capturado como path variable.
  @PutMapping("/reorder")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderInfoRequest body) {
    service.reorder(body.items());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public InfoSectionView update(
      @PathVariable UUID id, @Valid @RequestBody UpdateInfoSectionRequest body) {
    return service.update(id, body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('INFO_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
