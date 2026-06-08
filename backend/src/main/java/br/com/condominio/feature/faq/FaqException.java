package br.com.condominio.feature.faq;

/** Erros do FAQ mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, demais → 400. */
public class FaqException extends RuntimeException {

  private final String code;

  public FaqException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
