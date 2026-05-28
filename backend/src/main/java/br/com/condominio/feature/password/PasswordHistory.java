package br.com.condominio.feature.password;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

/**
 * Snapshot dos últimos hashes de senha do usuário. Usado por {@code User.changePassword()} pra
 * validar que a nova senha não bate com nenhuma das últimas 5 (per spec 4.5 / política).
 */
@Entity
@Table(name = "password_history")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id"})
public class PasswordHistory {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "password_pepper_version", nullable = false)
  private short passwordPepperVersion;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  public static PasswordHistory create(UUID userId, String passwordHash, short pepperVersion) {
    PasswordHistory h = new PasswordHistory();
    h.userId = userId;
    h.passwordHash = passwordHash;
    h.passwordPepperVersion = pepperVersion;
    return h;
  }
}
