package br.com.condominio.feature.contact.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateContactRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 60) String category,
    @NotBlank @Size(max = 20) String phone,
    @Size(max = 2000) String notes,
    boolean is24h,
    @Valid List<OpeningHoursDto> openingHours) {}
