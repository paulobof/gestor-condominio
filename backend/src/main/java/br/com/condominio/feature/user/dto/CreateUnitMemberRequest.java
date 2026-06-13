package br.com.condominio.feature.user.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Cadastro de morador pelo master. Senha é provisória (gerada pelo backend, mostrada 1x); não vem
 * no body. {@code gender} desserializado direto para o enum (valor inválido → 400).
 */
public record CreateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    Gender gender,
    LocalDate birthDate,
    boolean whatsappOptIn) {}
