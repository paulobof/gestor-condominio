package br.com.condominio.shared.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PepperedBCryptPasswordEncoder implements PasswordEncoder {

  static final String PENDING_PLACEHOLDER = "__PENDING__";

  private final byte[] pepper;
  private final int pepperVersion;
  private final BCryptPasswordEncoder bcrypt;

  public PepperedBCryptPasswordEncoder(
      byte[] pepper, int pepperVersion, BCryptPasswordEncoder bcrypt) {
    if (pepper == null || pepper.length < 32) {
      throw new IllegalArgumentException("Pepper must be at least 32 bytes");
    }
    this.pepper = pepper.clone();
    this.pepperVersion = pepperVersion;
    this.bcrypt = bcrypt;
  }

  public int pepperVersion() {
    return pepperVersion;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return bcrypt.encode(hmacBase64(rawPassword));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isEmpty()) return false;
    if (PENDING_PLACEHOLDER.equals(encodedPassword)) return false;
    return bcrypt.matches(hmacBase64(rawPassword), encodedPassword);
  }

  private String hmacBase64(CharSequence raw) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
      byte[] out = mac.doFinal(raw.toString().getBytes(StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
