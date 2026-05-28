package br.com.condominio.feature.password.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestResetRequest(@NotBlank @Email @Size(max = 180) String email) {}
