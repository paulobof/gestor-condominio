package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.Recommendation;
import br.com.condominio.feature.recommendation.RecommendationStatus;
import br.com.condominio.feature.tag.dto.TagView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationView(
    UUID id,
    String serviceName,
    String professionalName,
    String phone,
    boolean isResident,
    UUID residentUserId,
    String addressLine,
    String priceRange,
    Integer rating,
    String comment,
    UUID recommendedByUserId,
    RecommendationStatus status,
    Instant createdAt,
    List<TagView> tags,
    List<OpeningHoursDto> openingHours,
    List<RecommendationPhotoView> photos,
    String instagramUrl,
    String facebookUrl,
    String whatsappUrl,
    String catalogUrl,
    UUID ownerUnitId,
    String ownerUnitCode,
    int likeCount,
    int dislikeCount,
    String myVote,
    long commentCount) {

  public static RecommendationView of(
      Recommendation r,
      List<TagView> tags,
      List<OpeningHoursDto> openingHours,
      List<RecommendationPhotoView> photos,
      String myVote,
      long commentCount) {
    return new RecommendationView(
        r.getId(),
        r.getServiceName(),
        r.getProfessionalName(),
        r.getPhone(),
        r.isResident(),
        r.getResidentUserId(),
        r.getAddressLine(),
        r.getPriceRange(),
        r.getRating() == null ? null : r.getRating().intValue(),
        r.getComment(),
        r.getRecommendedByUserId(),
        r.getStatus(),
        r.getCreatedAt(),
        tags,
        openingHours,
        photos,
        r.getInstagramUrl(),
        r.getFacebookUrl(),
        r.getWhatsappUrl(),
        r.getCatalogUrl(),
        r.getOwnerUnitId(),
        r.getOwnerUnitCode(),
        r.getLikeCount(),
        r.getDislikeCount(),
        myVote,
        commentCount);
  }
}
