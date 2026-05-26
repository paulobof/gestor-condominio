package br.com.condominio.feature.unit;

import br.com.condominio.feature.unit.dto.UnitLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitLookupController {

  private final UnitService service;

  @GetMapping("/lookup")
  public ResponseEntity<UnitLookupResponse> lookup(@RequestParam("code") String code) {
    return service
        .lookupByCode(code)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
