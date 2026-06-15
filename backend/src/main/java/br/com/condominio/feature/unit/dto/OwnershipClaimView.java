package br.com.condominio.feature.unit.dto;

import java.time.Instant;
import java.util.UUID;

/** Pedido de posse pendente para a tela admin (sem PII de comprovante além do nome do arquivo). */
public record OwnershipClaimView(
    UUID id,
    UUID userId,
    String userName,
    UUID unitId,
    String unitCode,
    String proofFilename,
    Instant proofUploadedAt,
    Instant createdAt) {}
