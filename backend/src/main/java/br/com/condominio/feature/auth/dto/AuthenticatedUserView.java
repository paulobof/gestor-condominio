package br.com.condominio.feature.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUserView(
    UUID id,
    String fullName,
    String greetingName,
    String email,
    UUID unitId,
    boolean isUnitMaster,
    List<String> roles,
    List<String> authorities,
    boolean mustChangePassword) {}
