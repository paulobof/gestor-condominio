package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumberNormalizerTest {

  private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

  @Test
  void removeMascaraEmais() {
    assertThat(normalizer.toEvolutionNumber("+55 (11) 98888-7777")).isEqualTo("5511988887777");
  }

  @Test
  void prefixaDdiQuandoCelularSemDdi() {
    assertThat(normalizer.toEvolutionNumber("11988887777")).isEqualTo("5511988887777");
  }

  @Test
  void prefixaDdiQuandoFixoSemDdi() {
    assertThat(normalizer.toEvolutionNumber("1133334444")).isEqualTo("551133334444");
  }

  @Test
  void mantemCelularComDdi() {
    assertThat(normalizer.toEvolutionNumber("5511988887777")).isEqualTo("5511988887777");
  }

  @Test
  void mantemFixoComDdi() {
    assertThat(normalizer.toEvolutionNumber("551133334444")).isEqualTo("551133334444");
  }

  @Test
  void rejeitaCurtoDemais() {
    assertThatThrownBy(() -> normalizer.toEvolutionNumber("123"))
        .isInstanceOf(WhatsAppSendException.class);
  }

  @Test
  void rejeitaLongoDemais() {
    assertThatThrownBy(() -> normalizer.toEvolutionNumber("5511988887777999"))
        .isInstanceOf(WhatsAppSendException.class);
  }

  @Test
  void rejeitaNuloOuVazio() {
    assertThatThrownBy(() -> normalizer.toEvolutionNumber(null))
        .isInstanceOf(WhatsAppSendException.class);
    assertThatThrownBy(() -> normalizer.toEvolutionNumber("   "))
        .isInstanceOf(WhatsAppSendException.class);
  }

  @Test
  void mensagemDeErroNaoVazaTelefone() {
    assertThatThrownBy(() -> normalizer.toEvolutionNumber("12345"))
        .isInstanceOf(WhatsAppSendException.class)
        .hasMessageNotContaining("12345");
  }
}
