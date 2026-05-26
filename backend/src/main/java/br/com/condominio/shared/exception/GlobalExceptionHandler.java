package br.com.condominio.shared.exception;

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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);
    // TEMPORARIO PARA DIAGNOSTICO: revela detalhes da exception no body.
    // REVERTER apos resolver o bug do login.
    StringBuilder sb = new StringBuilder();
    sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage());
    Throwable cause = ex.getCause();
    int depth = 0;
    while (cause != null && depth < 5) {
      sb.append(" | caused by ")
          .append(cause.getClass().getName())
          .append(": ")
          .append(cause.getMessage());
      cause = cause.getCause();
      depth++;
    }
    return ResponseEntity.internalServerError()
        .body(
            ApiError.of(
                500, "Internal Server Error", "INTERNAL_ERROR", sb.toString(), requestId()));
  }

  private String requestId() {
    return MDC.get("requestId");
  }
}
