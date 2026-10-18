package br.com.condominio.feature.complaint;

/**
 * Erros de chamados mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, FORBIDDEN/NO_UNIT
 * → 403, INVALID_STATE/DUPLICATE_NAME/BATCH_STALE → 409, CATALOG_INVALID → 422, demais → 400.
 */
public class ComplaintException extends RuntimeException {
  private final String code;

  public ComplaintException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
