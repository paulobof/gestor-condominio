package br.com.condominio.feature.user;

import br.com.condominio.feature.user.dto.*;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/units/me/members")
@RequiredArgsConstructor
public class UnitMemberController {

  private final UnitMemberService service;

  @GetMapping
  public List<UnitMemberResponse> listMy(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    return service.listMyUnitMembers(me.userId());
  }

  @PostMapping
  public ResponseEntity<UnitMemberResponse> create(
      @Valid @RequestBody CreateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createMember(me.userId(), req));
  }

  @PutMapping("/{id}/disable")
  public ResponseEntity<Void> disable(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    requireMaster(me);
    service.disableMember(me.userId(), id);
    return ResponseEntity.noContent().build();
  }

  private void requireMaster(AuthenticatedUserPrincipal me) {
    if (me == null || !me.isUnitMaster()) {
      throw new AccessDeniedException("Apenas o master da unidade pode realizar esta ação.");
    }
  }
}
