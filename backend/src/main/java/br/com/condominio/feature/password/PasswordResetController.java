package br.com.condominio.feature.password;

import br.com.condominio.feature.password.dto.ConsumeResetRequest;
import br.com.condominio.feature.password.dto.RequestResetRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService service;

  /** Sempre 202 — não vaza existência do usuário. */
  @PostMapping("/request-reset")
  public ResponseEntity<Void> requestReset(
      @Valid @RequestBody RequestResetRequest body, HttpServletRequest request) {
    service.requestReset(body.email(), resolveClientIp(request));
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  /** 204 no sucesso; 400 PasswordResetException em token inválido/reuso. */
  @PostMapping("/consume-reset")
  public ResponseEntity<Void> consumeReset(
      @Valid @RequestBody ConsumeResetRequest body, HttpServletRequest request) {
    service.consumeReset(body.token(), body.newPassword(), resolveClientIp(request));
    return ResponseEntity.noContent().build();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
