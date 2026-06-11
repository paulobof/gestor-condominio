package br.com.condominio.shared.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Gera senha provisória forte (passa em {@code StrongPasswordValidator}): 16 chars, 1 de cada
 * classe.
 */
@Component
public class ProvisionalPasswordGenerator {

  private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
  private static final String DIGIT = "23456789";
  private static final String SPECIAL = "!@#$%*-_";
  private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

  private final SecureRandom rnd = new SecureRandom();

  public String generate() {
    char[] out = new char[16];
    out[0] = pick(UPPER);
    out[1] = pick(LOWER);
    out[2] = pick(DIGIT);
    out[3] = pick(SPECIAL);
    for (int i = 4; i < out.length; i++) {
      out[i] = pick(ALL);
    }
    for (int i = out.length - 1; i > 0; i--) {
      int j = rnd.nextInt(i + 1);
      char t = out[i];
      out[i] = out[j];
      out[j] = t;
    }
    return new String(out);
  }

  private char pick(String pool) {
    return pool.charAt(rnd.nextInt(pool.length()));
  }
}
