package br.com.condominio.feature.parkingrental;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-rentals")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.parkingrental.enabled", havingValue = "true")
public class ParkingRentalController {

  private final ParkingRentalService service;

  private static boolean canModerate(AuthenticatedUserPrincipal me) {
    return me.authorities().contains("PARKING_RENTAL_MODERATE");
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ParkingRentalView> list(
      @RequestParam(required = false) ParkingRentalStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    return service.list(status, PageRequest.of(safePage, safeSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ParkingRentalView get(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ParkingRentalView> create(
      @Valid @RequestBody CreateParkingRentalRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body));
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ParkingRentalView update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateParkingRentalRequest body,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.update(id, me.userId(), canModerate(me), body);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.delete(id, me.userId(), canModerate(me));
    return ResponseEntity.noContent().build();
  }
}
