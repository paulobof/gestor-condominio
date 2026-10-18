package br.com.condominio.feature.recommendation.dto;

import br.com.condominio.feature.recommendation.ReviewSentiment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code sentiment}/{@code rating}/{@code text} viram a primeira avaliação da indicação — todos
 * opcionais aqui porque a obrigatoriedade depende de o autor ter unidade (regra de domínio,
 * validada em {@code RecommendationService}, não anotação de campo).
 */
public record CreateRecommendationRequest(
    @NotBlank @Size(max = 120) String serviceName,
    @Size(max = 120) String professionalName,
    @Size(max = 20) String phone,
    boolean isResident,
    UUID residentUserId,
    @Size(max = 255) String addressLine,
    @Size(max = 40) String priceRange,
    ReviewSentiment sentiment,
    @Min(1) @Max(5) Integer rating,
    // Mesmo teto do ReviewRequest#text: este campo vira a primeira avaliação. Só o máximo aqui — o
    // mínimo de 10 não pode ser anotação porque o campo é opcional para autor sem unidade; quem
    // fecha os dois lados é a validação de domínio em RecommendationReviewService#validate.
    @Size(max = 2000) String text,
    List<String> tagSlugs,
    List<OpeningHoursDto> openingHours,
    @Size(max = 255) @Pattern(regexp = "^$|https?://.*", message = "URL deve começar com https://")
        String instagramUrl,
    @Size(max = 255) @Pattern(regexp = "^$|https?://.*", message = "URL deve começar com https://")
        String facebookUrl,
    @Size(max = 255) @Pattern(regexp = "^$|https?://.*", message = "URL deve começar com https://")
        String whatsappUrl,
    @Size(max = 500) @Pattern(regexp = "^$|https?://.*", message = "URL deve começar com https://")
        String catalogUrl) {}
