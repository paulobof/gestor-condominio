package br.com.condominio.feature.registration;

public class RegistrationException extends RuntimeException {
  private final String code;

  public RegistrationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
