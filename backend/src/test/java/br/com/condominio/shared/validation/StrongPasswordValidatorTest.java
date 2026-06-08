package br.com.condominio.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StrongPasswordValidatorTest {

  private final StrongPasswordValidator validator = new StrongPasswordValidator();

  private boolean valid(String v) {
    return validator.isValid(v, null);
  }

  @Test
  void accepts_strong_password() {
    assertThat(valid("Senha@1234")).isTrue();
  }

  @Test
  void rejects_too_short() {
    assertThat(valid("Aa1@bc")).isFalse();
  }

  @Test
  void rejects_without_uppercase() {
    assertThat(valid("senha@1234")).isFalse();
  }

  @Test
  void rejects_without_lowercase() {
    assertThat(valid("SENHA@1234")).isFalse();
  }

  @Test
  void rejects_without_digit() {
    assertThat(valid("Senha@abcd")).isFalse();
  }

  @Test
  void rejects_without_special() {
    assertThat(valid("Senha12345")).isFalse();
  }

  @Test
  void rejects_too_long() {
    // 132 chars, atende todas as classes mas excede o limite de 128.
    assertThat(valid("Aa1@".repeat(33))).isFalse();
  }

  @Test
  void rejects_empty_string() {
    assertThat(valid("")).isFalse();
  }

  @Test
  void null_defers_to_notblank() {
    assertThat(valid(null)).isTrue();
  }
}
