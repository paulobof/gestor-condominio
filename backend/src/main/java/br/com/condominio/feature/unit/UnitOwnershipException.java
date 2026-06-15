package br.com.condominio.feature.unit;

/**
 * Erros de posse de unidade mapeados em {@code GlobalExceptionHandler}: UNIT_NOT_FOUND → 404;
 * UNIT_HAS_OWNER/DUPLICATE_CLAIM → 409; CLAIM_NOT_FOUND → 404; demais → 400.
 */
public class UnitOwnershipException extends RuntimeException {

  private final String code;

  public UnitOwnershipException(String code, String message) {
    super(message);
    this.code = code;
  }

  public UnitOwnershipException(String code) {
    this(code, code);
  }

  public String getCode() {
    return code;
  }
}
