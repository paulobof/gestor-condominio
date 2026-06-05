package br.com.condominio.feature.recommendation;

public class RecommendationException extends RuntimeException {
  private final String code;

  public RecommendationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
