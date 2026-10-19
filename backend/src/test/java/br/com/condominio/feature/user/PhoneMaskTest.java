package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneMaskTest {

  @Test
  void mostraApenasOsUltimosQuatroDigitos() {
    assertThat(PhoneMask.lastFour("+5511999997777")).isEqualTo("•••• 7777");
  }

  @Test
  void ignoraCaracteresNaoNumericos() {
    assertThat(PhoneMask.lastFour(" (11) 99999-1234 ")).isEqualTo("•••• 1234");
  }

  @Test
  void naoVazaTelefoneCurtoNemNulo() {
    assertThat(PhoneMask.lastFour("123")).isEqualTo("••••");
    assertThat(PhoneMask.lastFour(null)).isEqualTo("••••");
    assertThat(PhoneMask.lastFour("  ")).isEqualTo("••••");
  }
}
