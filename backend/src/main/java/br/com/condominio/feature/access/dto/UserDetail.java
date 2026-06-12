package br.com.condominio.feature.access.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados completos do usuário para preencher o formulário de edição. Campos opcionais podem ser
 * nulos.
 */
public record UserDetail(
    UUID id,
    String fullName,
    String greetingName,
    String phone,
    String unitCode,
    String email,
    String gender,
    LocalDate birthDate) {}
