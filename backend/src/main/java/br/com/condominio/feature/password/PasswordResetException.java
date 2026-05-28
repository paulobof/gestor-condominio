package br.com.condominio.feature.password;

import lombok.Getter;

/** Erros de reset de senha mapeados em {@code GlobalExceptionHandler} → 400 BAD_REQUEST. */
@Getter
public class PasswordResetException extends RuntimeException {
  private final String code;

  public PasswordResetException(String code, String message) {
    super(message);
    this.code = code;
  }
}
