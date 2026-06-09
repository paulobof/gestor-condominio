package br.com.condominio.feature.info;

/**
 * Erros de Informações mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, demais → 400.
 */
public class InfoException extends RuntimeException {

  private final String code;

  public InfoException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
