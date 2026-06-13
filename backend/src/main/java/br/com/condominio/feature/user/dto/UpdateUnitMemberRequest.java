package br.com.condominio.feature.user.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Edição de morador pelo master. Sem {@code unitId} (o master não move o morador para outra
 * unidade) e sem senha. {@code gender} desserializado direto para o enum (valor inválido → 400).
 */
public record UpdateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    @NotBlank @Email @Size(max = 180) String email,
    Gender gender,
    LocalDate birthDate) {}
