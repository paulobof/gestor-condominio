package br.com.condominio.feature.user.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UnitMemberDetail(
    UUID id,
    String fullName,
    String greetingName,
    String phone,
    String email,
    String gender,
    LocalDate birthDate) {}
