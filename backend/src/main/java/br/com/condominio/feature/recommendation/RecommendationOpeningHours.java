package br.com.condominio.feature.recommendation;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "recommendation_opening_hours")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "dayOfWeek"})
public class RecommendationOpeningHours {

  @Id @GeneratedValue private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(name = "day_of_week", nullable = false)
  private short dayOfWeek;

  @Column(name = "opens_at")
  private LocalTime opensAt;

  @Column(name = "closes_at")
  private LocalTime closesAt;

  @Column(length = 120)
  private String notes;

  public static RecommendationOpeningHours create(
      UUID ownerId, short dayOfWeek, LocalTime opensAt, LocalTime closesAt, String notes) {
    RecommendationOpeningHours h = new RecommendationOpeningHours();
    h.ownerId = ownerId;
    h.dayOfWeek = dayOfWeek;
    h.opensAt = opensAt;
    h.closesAt = closesAt;
    h.notes = notes;
    return h;
  }
}
