package br.com.condominio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MagicBytesValidatorTest {

  private final MagicBytesValidator validator = new MagicBytesValidator();

  @Test
  void detectsPdfFromMagicBytes() {
    byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    assertThat(validator.detect(new ByteArrayInputStream(pdf))).isEqualTo("application/pdf");
  }

  @Test
  void detectsJpegFromMagicBytes() {
    byte[] jpeg = {
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'
    };
    assertThat(validator.detect(new ByteArrayInputStream(jpeg))).isEqualTo("image/jpeg");
  }

  @Test
  void detectsPngFromMagicBytes() {
    byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    assertThat(validator.detect(new ByteArrayInputStream(png))).isEqualTo("image/png");
  }

  @Test
  void acceptsValidContentTypes() {
    assertThat(validator.isAcceptedForProof("application/pdf")).isTrue();
    assertThat(validator.isAcceptedForProof("image/jpeg")).isTrue();
    assertThat(validator.isAcceptedForProof("image/png")).isTrue();
    assertThat(validator.isAcceptedForProof("image/webp")).isTrue();
  }

  @Test
  void rejectsInvalidContentTypes() {
    assertThat(validator.isAcceptedForProof("text/html")).isFalse();
    assertThat(validator.isAcceptedForProof("application/zip")).isFalse();
    assertThat(validator.isAcceptedForProof("application/x-executable")).isFalse();
  }

  @Test
  void detectsHtmlMasqueradingAsPdf() {
    byte[] html = "<html><body>fake</body></html>".getBytes();
    assertThat(validator.detect(new ByteArrayInputStream(html))).startsWith("text/html");
  }
}
