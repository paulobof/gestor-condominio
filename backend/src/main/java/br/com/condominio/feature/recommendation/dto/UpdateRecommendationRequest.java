package br.com.condominio.feature.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRecommendationRequest(
    @NotBlank @Size(max = 120) String serviceName,
    @Size(max = 120) String professionalName,
    @Size(max = 20) String phone,
    @Size(max = 255) String addressLine,
    @Size(max = 40) String priceRange,
    @Min(1) @Max(5) Integer rating,
    String comment,
    List<String> tagSlugs,
    List<OpeningHoursDto> openingHours) {}
