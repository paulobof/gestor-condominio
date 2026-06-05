package br.com.condominio.feature.recommendation.dto;

import java.util.UUID;

public record RecommendationPhotoView(UUID id, int ordering, String contentType) {}
