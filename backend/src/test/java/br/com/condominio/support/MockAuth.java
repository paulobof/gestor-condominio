package br.com.condominio.support;

import br.com.condominio.shared.security.AuthenticatedUserPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Constrói um {@link RequestPostProcessor} autenticado para testes de camada web ({@code MockMvc}).
 * Popula um {@link AuthenticatedUserPrincipal} idêntico ao que o {@code JwtAuthenticationConverter}
 * produz em runtime: as authorities entram tanto no token (para {@code hasAuthority(...)}) quanto
 * no campo {@code authorities()} do principal (para a checagem de moderação feita no controller).
 */
public final class MockAuth {

  private MockAuth() {}

  /** Usuário autenticado comum (não-master da unidade). */
  public static RequestPostProcessor user(UUID userId, String... authorities) {
    return build(userId, false, authorities);
  }

  /** Usuário autenticado que é master da unidade ({@code isUnitMaster=true}). */
  public static RequestPostProcessor master(UUID userId, String... authorities) {
    return build(userId, true, authorities);
  }

  /** Usuário autenticado com unidade específica. */
  public static RequestPostProcessor userWithUnit(UUID userId, UUID unitId, String... authorities) {
    List<String> auths = Arrays.asList(authorities);
    AuthenticatedUserPrincipal principal =
        new AuthenticatedUserPrincipal(userId, "Tester", List.of(), auths, unitId, false);
    List<SimpleGrantedAuthority> granted = auths.stream().map(SimpleGrantedAuthority::new).toList();
    return SecurityMockMvcRequestPostProcessors.authentication(
        new UsernamePasswordAuthenticationToken(principal, null, granted));
  }

  private static RequestPostProcessor build(UUID userId, boolean isUnitMaster, String... auths) {
    List<String> authorities = Arrays.asList(auths);
    AuthenticatedUserPrincipal principal =
        new AuthenticatedUserPrincipal(
            userId, "Tester", List.of(), authorities, null, isUnitMaster);
    List<SimpleGrantedAuthority> granted =
        authorities.stream().map(SimpleGrantedAuthority::new).toList();
    return SecurityMockMvcRequestPostProcessors.authentication(
        new UsernamePasswordAuthenticationToken(principal, null, granted));
  }
}
