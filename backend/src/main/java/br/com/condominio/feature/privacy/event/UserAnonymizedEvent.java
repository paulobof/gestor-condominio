package br.com.condominio.feature.privacy.event;

import java.util.UUID;

/**
 * Disparado após {@link br.com.condominio.feature.user.User#anonymize()} commitar. Listener async
 * apaga o object key do comprovante no MinIO (operação não-transacional).
 */
public record UserAnonymizedEvent(UUID userId, String residenceProofObjectKey) {}
