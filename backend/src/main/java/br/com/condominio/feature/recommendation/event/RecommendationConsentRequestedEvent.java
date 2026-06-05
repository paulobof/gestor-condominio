package br.com.condominio.feature.recommendation.event;

import java.util.UUID;

/**
 * Disparado após persistir uma indicação PENDING_RESIDENT_CONSENT. O listener async enfileira o
 * WhatsApp de consentimento para o morador indicado.
 */
public record RecommendationConsentRequestedEvent(
    UUID recommendationId,
    UUID residentUserId,
    String residentPhone,
    String residentGreetingName,
    String recommenderName,
    String serviceName) {}
