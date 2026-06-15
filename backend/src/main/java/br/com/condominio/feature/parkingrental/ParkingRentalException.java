package br.com.condominio.feature.parkingrental;

/**
 * Erros de aluguel de vagas mapeados em {@code GlobalExceptionHandler}: NOT_FOUND → 404, FORBIDDEN
 * → 403, demais → 400.
 */
public class ParkingRentalException extends RuntimeException {
  private final String code;

  public ParkingRentalException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
