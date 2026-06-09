package br.com.condominio.feature.access;

import br.com.condominio.feature.access.dto.AssignableRoleView;
import br.com.condominio.feature.access.dto.UserSearchResult;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestão de acessos. Toda a superfície exige {@code ROLE_ASSIGN} (hoje só do Síndico). Atrás da
 * feature flag {@code app.feature.accessmanagement.enabled}.
 */
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.accessmanagement.enabled", havingValue = "true")
public class AccessController {

  private final AccessService service;

  @GetMapping("/roles")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<AssignableRoleView> roles() {
    return service.assignableRoles();
  }

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<UserSearchResult> searchUsers(@RequestParam(name = "q", defaultValue = "") String q) {
    return service.searchUsers(q);
  }

  @GetMapping("/users/{id}/roles")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public List<Short> userRoles(@PathVariable UUID id) {
    return service.userRoleIds(id);
  }

  @PostMapping("/users/{id}/roles/{roleId}")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public ResponseEntity<Void> assign(
      @PathVariable UUID id,
      @PathVariable short roleId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.assign(me.userId(), id, roleId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/{id}/roles/{roleId}")
  @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
  public ResponseEntity<Void> remove(
      @PathVariable UUID id,
      @PathVariable short roleId,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.remove(me.userId(), id, roleId);
    return ResponseEntity.noContent().build();
  }
}
