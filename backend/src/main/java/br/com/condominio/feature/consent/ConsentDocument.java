package br.com.condominio.feature.consent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "consent_document")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class ConsentDocument {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 20)
  private String version;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;
}
