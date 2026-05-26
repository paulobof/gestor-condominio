package br.com.condominio.feature.role;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "user_role")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserRole {

  @EmbeddedId private UserRoleId id;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  @Column(name = "assigned_by_user_id")
  private UUID assignedByUserId;
}
