package br.com.condominio.feature.access.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados completos do usuário para preencher o formulário de edição. Campos opcionais podem ser
 * nulos. {@code pendingPassword}: ainda não criou a senha pelo convite (permite reenviar o link).
 */
public record UserDetail(
    UUID id,
    String fullName,
    String greetingName,
    String phone,
    String unitCode,
    String email,
    String gender,
    LocalDate birthDate,
    boolean pendingPassword) {}
