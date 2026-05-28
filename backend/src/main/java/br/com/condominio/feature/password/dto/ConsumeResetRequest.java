package br.com.condominio.feature.password.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsumeResetRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 128, message = "Senha deve ter entre 8 e 128 caracteres")
        String newPassword) {}
