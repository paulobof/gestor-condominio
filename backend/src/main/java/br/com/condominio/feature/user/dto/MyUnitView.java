package br.com.condominio.feature.user.dto;

import java.util.UUID;

/** Unidade sob gestão do usuário logado (para o seletor de unidade na gestão de moradores). */
public record MyUnitView(UUID unitId, String code) {}
