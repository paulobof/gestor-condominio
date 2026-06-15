package br.com.condominio.feature.document;

/**
 * Erros da feature de documentos, mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404;
 * demais (FILE_TYPE_INVALID, FILE_TOO_LARGE, TITLE_REQUIRED, ...) → 400.
 */
public class DocumentException extends RuntimeException {

  private final String code;

  public DocumentException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
