package br.com.condominio.feature.user;

import br.com.condominio.feature.user.dto.MyUnitView;
import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unidades sob gestão do usuário logado — base do seletor de unidade na gestão de moradores. */
@RestController
@RequestMapping("/api/units/me")
@RequiredArgsConstructor
public class MyUnitsController {

  private final UnitMemberService service;

  @GetMapping
  @PreAuthorize("hasAuthority('RESIDENT_MANAGE')")
  public List<MyUnitView> myUnits(@AuthenticationPrincipal AuthenticatedUserPrincipal me) {
    return service.listMyUnits(me.userId());
  }
}
