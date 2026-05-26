package br.com.condominio.feature.registration.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PendingRegistrationView(
    UUID userId,
    String fullName,
    String email,
    String phone,
    String unitCode,
    String gender,
    LocalDate birthDate,
    String residenceProofFilename,
    Instant residenceProofUploadedAt,
    Instant createdAt) {}
