package br.com.condominio.shared.validation;

/** Padrões de validação reutilizados entre DTOs (evita regex duplicada e drift). */
public final class ValidationPatterns {

  private ValidationPatterns() {}

  /** Telefone: '+' opcional seguido de 10 a 15 dígitos (normalizado para DDI no envio). */
  public static final String PHONE = "\\+?[0-9]{10,15}";
}
