package br.com.condominio.feature.contact;

/** Erros de Contato mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, demais → 400. */
public class ContactException extends RuntimeException {

  private final String code;

  public ContactException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
