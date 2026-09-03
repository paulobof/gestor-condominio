package br.com.condominio.feature.recommendation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominio.feature.recommendation.ReviewSentiment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReviewRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void construtor_aparaOTextoAntesDaValidacao() {
    ReviewRequest req = new ReviewRequest(ReviewSentiment.LIKE, 5, "  Serviço muito bom.  ");

    assertThat(req.text()).isEqualTo("Serviço muito bom.");
  }

  @Test
  void textoComPaddingQueAparadoFicaCurto_eRejeitadoPelaValidacao() {
    // 9 espaços + 1 caractere: passaria em @NotBlank/@Size(min=10) se validado cru, mas o
    // construtor compacto apara antes, então a validação vê "x" (1 caractere) e rejeita.
    ReviewRequest req = new ReviewRequest(ReviewSentiment.LIKE, 5, "         x");

    Set<ConstraintViolation<ReviewRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("text"));
  }

  @Test
  void textoComDezCaracteresApósAparar_ePassaNaValidacao() {
    ReviewRequest req = new ReviewRequest(ReviewSentiment.LIKE, 5, "  0123456789  ");

    Set<ConstraintViolation<ReviewRequest>> violations = validator.validate(req);

    assertThat(violations).isEmpty();
  }
}
