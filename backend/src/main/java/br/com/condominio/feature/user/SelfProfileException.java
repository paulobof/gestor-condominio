package br.com.condominio.feature.user;

/**
 * Erros do "Meu cadastro" (edição dos próprios dados e troca de senha). Mapeados em {@code
 * GlobalExceptionHandler}: {@code USER_NOT_FOUND} → 404; demais ({@code INVALID_PASSWORD}, {@code
 * PASSWORD_REUSED}) → 400.
 */
public class SelfProfileException extends RuntimeException {

  private final String code;

  public SelfProfileException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
