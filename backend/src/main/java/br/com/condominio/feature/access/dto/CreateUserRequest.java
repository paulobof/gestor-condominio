package br.com.condominio.feature.access.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Cadastro de usuário pelo admin. {@code unitId} opcional; {@code roleIds} ≥1. */
public record CreateUserRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    UUID unitId,
    @NotEmpty List<Short> roleIds) {}
