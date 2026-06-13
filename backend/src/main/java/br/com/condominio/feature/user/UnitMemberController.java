package br.com.condominio.feature.user;

import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberDetail;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestão de moradores pelo morador master da unidade. Toda a superfície exige a permission {@code
 * RESIDENT_MANAGE} (concedida aos masters); o escopo "só a minha unidade" é garantido no service.
 */
@RestController
@RequestMapping("/api/units/me/members")
@RequiredArgsConstructor
public class UnitMemberController {

  private final UnitMemberService service;

  @GetMapping
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public List<UnitMemberResponse> listMy(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.listMyUnitMembers(me.userId());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public UnitMemberDetail getMemberDetail(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.getMemberDetail(me.userId(), id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<CreatedUnitMemberResponse> create(
      @Valid @RequestBody CreateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createMember(me.userId(), req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<Void> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUnitMemberRequest req,
      @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.updateMember(me.userId(), id, req);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    service.deleteMember(me.userId(), id);
    return ResponseEntity.noContent().build();
  }
}
