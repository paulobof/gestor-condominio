package br.com.condominio.feature.auth;

import br.com.condominio.shared.security.RateLimitProperties;
import br.com.condominio.shared.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class LoginAttemptTracker {

  private final RateLimitProperties props;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, Attempt> attempts = new ConcurrentHashMap<>();

  public boolean isLocked(UUID userId) {
    Attempt a = attempts.get(userId);
    if (a == null) return false;
    return a.failures() >= props.getLoginLockoutAttempts()
        && a.windowStart().plus(props.getLoginLockoutWindow()).isAfter(clock.now());
  }

  public void recordFailure(UUID userId) {
    attempts.compute(
        userId,
        (k, prev) -> {
          if (prev == null
              || prev.windowStart().plus(props.getLoginLockoutWindow()).isBefore(clock.now())) {
            return new Attempt(1, clock.now());
          }
          return new Attempt(prev.failures() + 1, prev.windowStart());
        });
  }

  public void recordSuccess(UUID userId) {
    attempts.remove(userId);
  }

  private record Attempt(int failures, Instant windowStart) {}
}
