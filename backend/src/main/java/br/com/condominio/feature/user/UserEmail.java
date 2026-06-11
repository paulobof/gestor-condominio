package br.com.condominio.feature.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_email")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "email"})
@SQLDelete(sql = "UPDATE user_email SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserEmail {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "email", nullable = false, columnDefinition = "citext")
  private String email;

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "created_at", updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at")
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Cria o e-mail primário de um usuário. */
  public static UserEmail primary(UUID userId, String email) {
    UserEmail e = new UserEmail();
    e.userId = userId;
    e.email = email;
    e.isPrimary = true;
    return e;
  }

  /** Troca o endereço de e-mail (login). Unicidade é validada no service. */
  public void changeEmail(String email) {
    this.email = email;
  }
}
