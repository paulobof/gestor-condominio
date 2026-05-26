package br.com.condominio.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter extends OncePerRequestFilter {

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        JwtService.ParsedAccessToken parsed = jwtService.parseAccessToken(token);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        parsed.authorities().forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));
        parsed.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        AuthenticatedUserPrincipal principal =
            new AuthenticatedUserPrincipal(
                parsed.userId(),
                null,
                parsed.roles(),
                parsed.authorities(),
                parsed.unitId(),
                parsed.isUnitMaster());
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put("userId", parsed.userId().toString());
      } catch (Exception e) {
        log.debug("JWT validation failed: {}", e.getMessage());
        // Não 401 aqui — deixa o entrypoint do Spring decidir. SecurityContext fica vazio.
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("userId");
    }
  }
}
