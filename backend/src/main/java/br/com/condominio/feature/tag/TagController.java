package br.com.condominio.feature.tag;

import br.com.condominio.feature.tag.dto.CreateTagRequest;
import br.com.condominio.feature.tag.dto.TagView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.recommendations.enabled", havingValue = "true")
public class TagController {

  private final TagService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<TagView> autocomplete(@RequestParam(name = "q", required = false) String q) {
    return service.searchForAutocomplete(q);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TagView> create(@Valid @RequestBody CreateTagRequest body) {
    Tag t = service.getOrCreate(body.slug(), body.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(TagView.of(t));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('TAG_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
