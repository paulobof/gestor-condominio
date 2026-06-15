package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.tag.Tag;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "recommendation")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "serviceName", "status"})
@SQLDelete(sql = "UPDATE recommendation SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Recommendation {

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  @Column(name = "service_name", nullable = false, length = 120)
  private String serviceName;

  @Column(name = "professional_name", length = 120)
  private String professionalName;

  @Column(length = 20)
  private String phone;

  @Column(name = "is_resident", nullable = false)
  private boolean resident;

  @Column(name = "resident_user_id")
  private UUID residentUserId;

  @Column(name = "address_line", length = 255)
  private String addressLine;

  @Column(name = "price_range", length = 40)
  private String priceRange;

  @Column private Short rating;

  @Column(columnDefinition = "text")
  private String comment;

  @Column(name = "instagram_url", length = 255)
  private String instagramUrl;

  @Column(name = "facebook_url", length = 255)
  private String facebookUrl;

  @Column(name = "whatsapp_url", length = 255)
  private String whatsappUrl;

  @Column(name = "catalog_url", length = 500)
  private String catalogUrl;

  @Column(name = "like_count", nullable = false)
  private int likeCount;

  @Column(name = "dislike_count", nullable = false)
  private int dislikeCount;

  @Column(name = "owner_unit_id", updatable = false)
  private UUID ownerUnitId;

  @Column(name = "owner_unit_code", length = 10, updatable = false)
  private String ownerUnitCode;

  @Column(name = "recommended_by_user_id", nullable = false, updatable = false)
  private UUID recommendedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RecommendationStatus status;

  @ManyToMany
  @JoinTable(
      name = "recommendation_tag",
      joinColumns = @JoinColumn(name = "recommendation_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<Tag> tags = new HashSet<>();

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static Recommendation create(
      UUID recommendedByUserId,
      String serviceName,
      String professionalName,
      String phone,
      boolean resident,
      UUID residentUserId,
      String addressLine,
      String priceRange,
      Integer rating,
      String comment,
      String instagramUrl,
      String facebookUrl,
      String whatsappUrl,
      String catalogUrl,
      UUID ownerUnitId,
      String ownerUnitCode) {
    Recommendation r = new Recommendation();
    r.recommendedByUserId = recommendedByUserId;
    r.serviceName = serviceName;
    r.professionalName = professionalName;
    r.phone = phone;
    r.resident = resident;
    r.residentUserId = residentUserId;
    r.addressLine = addressLine;
    r.priceRange = priceRange;
    r.rating = rating == null ? null : rating.shortValue();
    r.comment = comment;
    r.instagramUrl = instagramUrl;
    r.facebookUrl = facebookUrl;
    r.whatsappUrl = whatsappUrl;
    r.catalogUrl = catalogUrl;
    r.ownerUnitId = ownerUnitId;
    r.ownerUnitCode = ownerUnitCode;
    r.status = RecommendationStatus.ACTIVE;
    return r;
  }

  public void edit(
      String serviceName,
      String professionalName,
      String phone,
      String addressLine,
      String priceRange,
      Integer rating,
      String comment,
      String instagramUrl,
      String facebookUrl,
      String whatsappUrl,
      String catalogUrl) {
    this.serviceName = serviceName;
    this.professionalName = professionalName;
    this.phone = phone;
    this.addressLine = addressLine;
    this.priceRange = priceRange;
    this.rating = rating == null ? null : rating.shortValue();
    this.comment = comment;
    this.instagramUrl = instagramUrl;
    this.facebookUrl = facebookUrl;
    this.whatsappUrl = whatsappUrl;
    this.catalogUrl = catalogUrl;
  }

  public void replaceTags(Set<Tag> newTags) {
    this.tags.clear();
    this.tags.addAll(newTags);
  }

  public void hide() {
    if (status == RecommendationStatus.HIDDEN) {
      throw new IllegalStateException("Indicação já está oculta.");
    }
    status = RecommendationStatus.HIDDEN;
  }

  /** Atualiza os contadores denormalizados de votos (recomputados de recommendation_vote). */
  public void updateVoteCounts(int likeCount, int dislikeCount) {
    this.likeCount = likeCount;
    this.dislikeCount = dislikeCount;
  }
}
