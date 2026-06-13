package br.com.condominio.feature.user;

/**
 * Erros da gestão de moradores pelo master. Mapeados em {@code GlobalExceptionHandler}: {@code
 * MEMBER_NOT_IN_UNIT} → 403 (alvo fora da unidade do master ou é master); demais reusam {@code
 * AccessException} (EMAIL_TAKEN → 409, etc.).
 */
public class UnitMemberException extends RuntimeException {

  private final String code;

  public UnitMemberException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
