package br.com.condominio.feature.privacy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pacote de exportação do titular (LGPD Art. 18, II). Inclui todos os dados pessoais que o sistema
 * mantém — exceto o passwordHash (segredo, não considerado dado pessoal sob LGPD pois é derivação
 * one-way).
 */
public record PersonalDataExportResponse(
    UUID userId,
    String fullName,
    String greetingName,
    List<String> emails,
    String phone,
    Instant phoneVerifiedAt,
    String gender,
    LocalDate birthDate,
    String status,
    UnitInfo unit,
    ConsentInfo consent,
    boolean whatsappOptIn,
    Instant whatsappOptInAt,
    Instant createdAt,
    Instant updatedAt,
    List<String> roles,
    Instant exportedAt) {

  public record UnitInfo(UUID unitId, String code, boolean isUnitMaster) {}

  public record ConsentInfo(String documentVersion, Instant acceptedAt) {}
}
