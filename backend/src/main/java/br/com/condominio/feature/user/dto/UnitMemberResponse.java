package br.com.condominio.feature.user.dto;

import java.util.UUID;

public record UnitMemberResponse(
    UUID id, String fullName, String greetingName, String email, String phone, String status) {}
