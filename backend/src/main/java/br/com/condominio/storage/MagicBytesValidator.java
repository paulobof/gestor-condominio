package br.com.condominio.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class MagicBytesValidator {

  static final Set<String> ALLOWED_PROOF_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

  static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  private final Tika tika = new Tika();

  public String detect(InputStream input) {
    try {
      return tika.detect(input);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to detect MIME from stream", e);
    }
  }

  public boolean isAcceptedForProof(String contentType) {
    return contentType != null && ALLOWED_PROOF_TYPES.contains(contentType);
  }

  public boolean isAcceptedForPhoto(String contentType) {
    return contentType != null && ALLOWED_PHOTO_TYPES.contains(contentType);
  }
}
