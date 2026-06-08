package br.com.condominio.feature.password.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ConsumeResetRequest(
    @NotBlank String token, @NotBlank @StrongPassword String newPassword) {}
