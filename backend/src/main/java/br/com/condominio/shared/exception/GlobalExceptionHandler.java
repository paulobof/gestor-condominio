package br.com.condominio.shared.exception;

import br.com.condominio.feature.announcement.AnnouncementException;
import br.com.condominio.feature.classified.ClassifiedException;
import br.com.condominio.feature.contact.ContactException;
import br.com.condominio.feature.faq.FaqException;
import br.com.condominio.feature.password.PasswordResetException;
import br.com.condominio.feature.privacy.PrivacyException;
import br.com.condominio.feature.recommendation.RecommendationException;
import br.com.condominio.feature.registration.RegistrationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(RegistrationException.class)
  public ResponseEntity<ApiError> handleRegistration(RegistrationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiError.of(400, "Bad Request", ex.getCode(), ex.getMessage(), requestId()));
  }

  @ExceptionHandler(PasswordResetException.class)
  public ResponseEntity<ApiError> handlePasswordReset(PasswordResetException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiError.of(400, "Bad Request", ex.getCode(), ex.getMessage(), requestId()));
  }

  @ExceptionHandler(PrivacyException.class)
  public ResponseEntity<ApiError> handlePrivacy(PrivacyException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "INVALID_PASSWORD" -> HttpStatus.UNAUTHORIZED;
          case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(ClassifiedException.class)
  public ResponseEntity<ApiError> handleClassified(ClassifiedException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(RecommendationException.class)
  public ResponseEntity<ApiError> handleRecommendation(RecommendationException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(AnnouncementException.class)
  public ResponseEntity<ApiError> handleAnnouncement(AnnouncementException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(FaqException.class)
  public ResponseEntity<ApiError> handleFaq(FaqException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(ContactException.class)
  public ResponseEntity<ApiError> handleContact(ContactException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(
            ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                requestId()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError.FieldError> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest()
        .body(ApiError.validation("Verifique os campos.", fields, requestId()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiError.of(403, "Forbidden", "ACCESS_DENIED", "Acesso negado.", requestId()));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiError.of(
                401,
                "Unauthorized",
                "INVALID_CREDENTIALS",
                "E-mail ou senha inválidos, ou cadastro não ativo.",
                requestId()));
  }

  /**
   * Falha de refresh token (desconhecido/expirado/replay) é lançada como {@link SecurityException}
   * pelo {@code RefreshTokenService}. É 401 (sessão inválida → relogar), não 500 — o cliente trata
   * o 401 limpando a sessão e indo pro login. Não logar o token; mensagem genérica.
   */
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiError> handleSecurity(SecurityException ex) {
    log.warn("Refresh/security rejeitado: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiError.of(
                401,
                "Unauthorized",
                "SESSION_INVALID",
                "Sessão expirada ou inválida. Faça login novamente.",
                requestId()));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(
                409,
                "Conflict",
                "CONCURRENT_MODIFICATION",
                "Outro usuário modificou este recurso. Recarregue e tente novamente.",
                requestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleBusinessRule(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(409, "Conflict", "BUSINESS_RULE_VIOLATION", ex.getMessage(), requestId()));
  }

  /**
   * Rota/recurso não mapeado (ex.: endpoint atrás de feature flag desligada). Sem este handler, o
   * catch-all {@link #handleGeneric} transformaria o 404 do framework em 500, poluindo métricas de
   * erro e disparando alertas para 404s benignos.
   */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ApiError> handleNotFound(Exception ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(404, "Not Found", "NOT_FOUND", "Recurso não encontrado.", requestId()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.internalServerError()
        .body(
            ApiError.of(
                500,
                "Internal Server Error",
                "INTERNAL_ERROR",
                "Erro interno do servidor.",
                requestId()));
  }

  private String requestId() {
    return MDC.get("requestId");
  }
}
