package br.com.condominio.feature.contact.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContactView(
    UUID id,
    String name,
    String category,
    String phone,
    String notes,
    boolean is24h,
    List<OpeningHoursDto> openingHours,
    Instant updatedAt) {}
