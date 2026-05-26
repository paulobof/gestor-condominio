package br.com.condominio.feature.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditWriter {

  private final ProofAccessLogRepository proofRepo;
  private final SensitiveAccessLogRepository sensitiveRepo;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logProofAccess(
      UUID adminUserId, UUID targetUserId, HttpServletRequest request, int ttlSeconds) {
    ProofAccessLog log =
        new ProofAccessLog(
            null,
            adminUserId,
            targetUserId,
            Instant.now(),
            resolveIp(request),
            shortenUa(request.getHeader("User-Agent")),
            ttlSeconds,
            MDC.get("requestId"));
    proofRepo.save(log);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logSensitiveAccess(
      UUID actorUserId, UUID targetUserId, String action, HttpServletRequest request) {
    SensitiveAccessLog log =
        new SensitiveAccessLog(
            null,
            actorUserId,
            targetUserId,
            action,
            Instant.now(),
            resolveIp(request),
            shortenUa(request.getHeader("User-Agent")),
            MDC.get("requestId"));
    sensitiveRepo.save(log);
  }

  private String resolveIp(HttpServletRequest r) {
    String fwd = r.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return r.getRemoteAddr();
  }

  private String shortenUa(String ua) {
    if (ua == null) return null;
    return ua.length() > 250 ? ua.substring(0, 250) : ua;
  }
}
