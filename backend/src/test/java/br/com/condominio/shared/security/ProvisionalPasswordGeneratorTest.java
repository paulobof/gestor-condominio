package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominio.shared.validation.StrongPasswordValidator;
import org.junit.jupiter.api.RepeatedTest;

class ProvisionalPasswordGeneratorTest {

  private final ProvisionalPasswordGenerator generator = new ProvisionalPasswordGenerator();
  private final StrongPasswordValidator validator = new StrongPasswordValidator();

  @RepeatedTest(50)
  void generatesPasswordThatPassesStrongPolicy() {
    String pw = generator.generate();
    assertThat(pw).hasSizeGreaterThanOrEqualTo(8);
    assertThat(validator.isValid(pw, null)).isTrue();
  }
}
