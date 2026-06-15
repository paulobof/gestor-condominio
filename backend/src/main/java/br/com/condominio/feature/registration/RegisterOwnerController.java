package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterOwnerRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature.unitownership.enabled", havingValue = "true")
public class RegisterOwnerController {

  private final RegistrationService service;

  @PostMapping(value = "/register-owner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerOwner(
      @Valid @ModelAttribute RegisterOwnerRequest req,
      @RequestPart("proof") MultipartFile proof,
      HttpServletRequest request) {
    String ip = resolveClientIp(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.registerOwner(req, proof, ip));
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
