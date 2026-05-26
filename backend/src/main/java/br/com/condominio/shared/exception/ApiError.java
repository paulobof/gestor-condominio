package br.com.condominio.shared.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    List<FieldError> fields,
    String requestId) {

  public record FieldError(String field, String message) {}

  public static ApiError of(
      int status, String error, String code, String message, String requestId) {
    return new ApiError(Instant.now(), status, error, code, message, null, requestId);
  }

  public static ApiError validation(String message, List<FieldError> fields, String requestId) {
    return new ApiError(
        Instant.now(), 400, "Bad Request", "VALIDATION_FAILED", message, fields, requestId);
  }
}
