package br.com.condominio.feature.access.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Edição de dados do usuário pelo admin. {@code unitId}/{@code greetingName}/{@code gender}/{@code
 * birthDate} opcionais.
 */
public record UpdateUserRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    UUID unitId,
    @NotBlank @Email @Size(max = 180) String email,
    String gender,
    LocalDate birthDate) {}
