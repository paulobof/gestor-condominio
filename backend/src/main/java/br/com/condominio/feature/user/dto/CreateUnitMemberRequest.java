package br.com.condominio.feature.user.dto;

import br.com.condominio.feature.user.Gender;
import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cadastro de morador pelo master. Senha é provisória (gerada pelo backend, mostrada 1x); não vem
 * no body. {@code gender} desserializado direto para o enum (valor inválido → 400). {@code unitId}
 * é opcional: quando o master tem mais de uma unidade (posse), indica em qual cadastrar; ausente
 * mantém o comportamento single-unit.
 */
public record CreateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = ValidationPatterns.PHONE) String phone,
    Gender gender,
    LocalDate birthDate,
    boolean whatsappOptIn,
    UUID unitId) {}
