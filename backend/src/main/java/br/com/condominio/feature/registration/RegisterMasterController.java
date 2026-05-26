package br.com.condominio.feature.registration;

import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegisterMasterController {

  private final RegistrationService service;

  @PostMapping(value = "/register-master", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<RegistrationStatusResponse> registerMaster(
      @Valid @ModelAttribute RegisterMasterRequest req,
      @RequestPart("proof") MultipartFile proof,
      HttpServletRequest request) {
    String ip = resolveClientIp(request);
    RegistrationStatusResponse resp = service.registerMaster(req, proof, ip);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }

  private String resolveClientIp(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
