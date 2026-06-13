package br.com.condominio.shared.security;

import br.com.condominio.shared.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(RateLimitProperties.class)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties props;
  private final ObjectMapper objectMapper;
  private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> registerGuestBuckets = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getServletPath();
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    Bucket bucket = null;
    if ("/api/auth/login".equals(path)) {
      bucket =
          loginBuckets.computeIfAbsent(
              clientIp(request),
              k -> newBucket(props.getLoginPerMinPerIp(), Duration.ofMinutes(1)));
    } else if ("/api/auth/refresh".equals(path)) {
      bucket =
          refreshBuckets.computeIfAbsent(
              clientIp(request),
              k -> newBucket(props.getRefreshPerMinPerIp(), Duration.ofMinutes(1)));
    } else if ("/api/auth/register-guest".equals(path)) {
      bucket =
          registerGuestBuckets.computeIfAbsent(
              clientIp(request),
              k -> newBucket(props.getRegisterGuestPerMinPerIp(), Duration.ofMinutes(1)));
    }
    if (bucket != null && !bucket.tryConsume(1)) {
      writeTooManyRequests(response, path);
      return;
    }
    chain.doFilter(request, response);
  }

  private Bucket newBucket(int capacity, Duration window) {
    Bandwidth limit =
        Bandwidth.builder().capacity(capacity).refillIntervally(capacity, window).build();
    return Bucket.builder().addLimit(limit).build();
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
    return request.getRemoteAddr();
  }

  private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
    log.warn("Rate limit hit on {}", path);
    response.setStatus(429);
    response.setContentType("application/json");
    ApiError err =
        ApiError.of(
            429,
            "Too Many Requests",
            "RATE_LIMIT_EXCEEDED",
            "Muitas tentativas. Aguarde alguns minutos.",
            MDC.get("requestId"));
    response.getWriter().write(objectMapper.writeValueAsString(err));
  }
}
