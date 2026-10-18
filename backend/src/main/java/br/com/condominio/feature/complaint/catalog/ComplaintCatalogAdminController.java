package br.com.condominio.feature.complaint.catalog;

import br.com.condominio.feature.complaint.catalog.dto.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/complaint-catalog")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.complaints.enabled", havingValue = "true")
public class ComplaintCatalogAdminController {

  private static final String CAN = "hasAuthority('COMPLAINT_CATALOG_MANAGE')";

  private final ComplaintCatalogService service;

  @GetMapping
  @PreAuthorize(CAN)
  public CatalogView all() {
    return service.catalog(true);
  }

  @PostMapping("/types")
  @PreAuthorize(CAN)
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogTypeView createType(@Valid @RequestBody CatalogItemRequest body) {
    return service.createType(body);
  }

  @PutMapping("/types/{id}")
  @PreAuthorize(CAN)
  public CatalogTypeView updateType(
      @PathVariable UUID id, @Valid @RequestBody CatalogItemRequest body) {
    return service.updateType(id, body);
  }

  @PostMapping("/types/{typeId}/subtypes")
  @PreAuthorize(CAN)
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogItemView createSubtype(
      @PathVariable UUID typeId, @Valid @RequestBody CatalogItemRequest body) {
    return service.createSubtype(typeId, body);
  }

  @PutMapping("/subtypes/{id}")
  @PreAuthorize(CAN)
  public CatalogItemView updateSubtype(
      @PathVariable UUID id, @Valid @RequestBody CatalogItemRequest body) {
    return service.updateSubtype(id, body);
  }

  @PostMapping("/locations")
  @PreAuthorize(CAN)
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogItemView createLocation(@Valid @RequestBody CatalogItemRequest body) {
    return service.createLocation(body);
  }

  @PutMapping("/locations/{id}")
  @PreAuthorize(CAN)
  public CatalogItemView updateLocation(
      @PathVariable UUID id, @Valid @RequestBody CatalogItemRequest body) {
    return service.updateLocation(id, body);
  }
}
