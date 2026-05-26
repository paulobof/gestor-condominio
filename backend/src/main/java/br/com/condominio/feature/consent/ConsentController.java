package br.com.condominio.feature.consent;

import br.com.condominio.feature.consent.dto.ConsentDocumentView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/privacy/document")
@RequiredArgsConstructor
public class ConsentController {

  private final ConsentService service;

  @GetMapping("/current")
  public ResponseEntity<ConsentDocumentView> current() {
    return service
        .current()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
