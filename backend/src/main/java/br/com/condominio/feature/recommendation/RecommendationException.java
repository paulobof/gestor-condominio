package br.com.condominio.feature.recommendation;

/**
 * Erros de indicações mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, FORBIDDEN → 403,
 * INVALID_STATE/PHOTO_* → 400.
 */
public class RecommendationException extends RuntimeException {
  private final String code;

  public RecommendationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
