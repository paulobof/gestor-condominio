package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterGuestRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegisterGuestController {

  private final RegistrationService service;

  @PostMapping(value = "/register-guest", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerGuest(
      @Valid @RequestBody RegisterGuestRequest req, HttpServletRequest request) {
    String ip = resolveClientIp(request);
    RegistrationStatusResponse resp = service.registerGuest(req, ip);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
