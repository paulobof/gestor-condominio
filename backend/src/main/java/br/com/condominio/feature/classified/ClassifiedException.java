package br.com.condominio.feature.classified;

/**
 * Erros de classifieds mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, FORBIDDEN →
 * 403, PHOTO_* → 400.
 */
public class ClassifiedException extends RuntimeException {
  private final String code;

  public ClassifiedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
