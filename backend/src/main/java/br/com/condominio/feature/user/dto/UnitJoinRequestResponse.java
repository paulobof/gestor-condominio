package br.com.condominio.feature.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de acesso à unidade aguardando o master. Sem comprovante: o master decide por quem é a
 * pessoa, não por documento.
 */
public record UnitJoinRequestResponse(
    UUID id,
    String fullName,
    String greetingName,
    String email,
    String phone,
    UUID unitId,
    String unitCode,
    Instant requestedAt) {}
