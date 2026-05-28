package br.com.condominio.feature.privacy;

import lombok.Getter;

/** Erros do fluxo LGPD self-service. */
@Getter
public class PrivacyException extends RuntimeException {
  private final String code;

  public PrivacyException(String code, String message) {
    super(message);
    this.code = code;
  }
}
