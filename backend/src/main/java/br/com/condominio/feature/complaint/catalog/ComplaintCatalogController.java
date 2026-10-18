package br.com.condominio.feature.complaint.catalog;

import br.com.condominio.feature.complaint.catalog.dto.CatalogView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/complaints/catalog")
@RequiredArgsConstructor
public class ComplaintCatalogController {

  private final ComplaintCatalogService service;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public CatalogView catalog() {
    return service.catalog(false);
  }
}
