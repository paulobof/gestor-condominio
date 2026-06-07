package br.com.condominio.feature.announcement;

/** Erros do mural mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, demais → 400. */
public class AnnouncementException extends RuntimeException {

  private final String code;

  public AnnouncementException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
