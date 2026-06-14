package br.com.condominio.feature.parkingrental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateParkingRentalRequest(
    @NotBlank @Size(max = 40) String tower,
    @NotBlank @Size(max = 20) String floor,
    @NotBlank @Size(max = 40) String spotNumber,
    @NotNull @Positive BigDecimal monthlyPrice) {}
