package br.com.condominio.feature.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;

public record OpeningHoursDto(
    @Min(0) @Max(6) int dayOfWeek, LocalTime opensAt, LocalTime closesAt, String notes) {}
