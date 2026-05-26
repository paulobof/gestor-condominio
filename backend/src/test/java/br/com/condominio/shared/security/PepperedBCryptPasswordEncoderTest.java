package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PepperedBCryptPasswordEncoderTest {

  private static final byte[] PEPPER_V1 =
      Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
  private static final byte[] PEPPER_V2 =
      Base64.getDecoder().decode("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");

  private PepperedBCryptPasswordEncoder encoder;

  @BeforeEach
  void setUp() {
    encoder = new PepperedBCryptPasswordEncoder(PEPPER_V1, 1, new BCryptPasswordEncoder(4));
  }

  @Test
  void encodesAndMatchesSamePassword() {
    String hash = encoder.encode("Hunter2!@");
    assertThat(encoder.matches("Hunter2!@", hash)).isTrue();
  }

  @Test
  void rejectsWrongPassword() {
    String hash = encoder.encode("Hunter2!@");
    assertThat(encoder.matches("hunter2!@", hash)).isFalse();
  }

  @Test
  void rejectsPendingPlaceholderExplicitly() {
    assertThat(encoder.matches("__PENDING__", "__PENDING__")).isFalse();
    assertThat(encoder.matches("anything", "__PENDING__")).isFalse();
  }

  @Test
  void differentPepperProducesDifferentHash() {
    PepperedBCryptPasswordEncoder enc1 =
        new PepperedBCryptPasswordEncoder(PEPPER_V1, 1, new BCryptPasswordEncoder(4));
    PepperedBCryptPasswordEncoder enc2 =
        new PepperedBCryptPasswordEncoder(PEPPER_V2, 2, new BCryptPasswordEncoder(4));
    String h1 = enc1.encode("samepass");
    String h2 = enc2.encode("samepass");
    assertThat(h1).isNotEqualTo(h2);
    assertThat(enc1.matches("samepass", h1)).isTrue();
    assertThat(enc2.matches("samepass", h1)).isFalse();
  }

  @Test
  void encodeIsNonDeterministicButMatchesBothOutputs() {
    String h1 = encoder.encode("Hunter2!@");
    String h2 = encoder.encode("Hunter2!@");
    assertThat(h1).isNotEqualTo(h2);
    assertThat(encoder.matches("Hunter2!@", h1)).isTrue();
    assertThat(encoder.matches("Hunter2!@", h2)).isTrue();
  }
}
