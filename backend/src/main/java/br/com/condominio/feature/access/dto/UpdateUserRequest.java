package br.com.condominio.feature.access.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Edição de dados do usuário pelo admin. {@code unitId}/{@code greetingName}/{@code gender}/{@code
 * birthDate} opcionais. {@code gender} é desserializado direto para o enum (valor inválido → 400).
 */
public record UpdateUserRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    UUID unitId,
    @NotBlank @Email @Size(max = 180) String email,
    Gender gender,
    LocalDate birthDate) {}
