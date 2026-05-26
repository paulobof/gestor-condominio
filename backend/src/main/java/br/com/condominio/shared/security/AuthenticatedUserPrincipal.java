package br.com.condominio.shared.security;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUserPrincipal(
    UUID userId,
    String displayName,
    List<String> roles,
    List<String> authorities,
    UUID unitId,
    boolean isUnitMaster) {}
