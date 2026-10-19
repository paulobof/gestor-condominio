package br.com.condominio.feature.user.dto;

import java.util.UUID;

/** Morador da unidade. {@code pendingPassword}: ainda não criou a senha pelo convite. */
public record UnitMemberResponse(
    UUID id,
    String fullName,
    String greetingName,
    String email,
    String phone,
    String status,
    UUID unitId,
    String unitCode,
    boolean pendingPassword) {}
