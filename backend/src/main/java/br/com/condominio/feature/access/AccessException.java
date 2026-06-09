package br.com.condominio.feature.access;

/**
 * Erros de gestão de acessos mapeados em {@code GlobalExceptionHandler}: ROLE_LIMIT_REACHED → 409;
 * ROLE_NOT_FOUND/USER_NOT_FOUND → 404; ROLE_NOT_ASSIGNABLE/USER_NOT_ACTIVE → 422; demais → 400.
 */
public class AccessException extends RuntimeException {

  private final String code;

  public AccessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
