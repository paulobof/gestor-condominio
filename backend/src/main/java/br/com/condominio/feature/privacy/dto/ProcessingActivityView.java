package br.com.condominio.feature.privacy.dto;

import java.util.List;

/**
 * Atividade de tratamento de dados pessoais (Art. 9 LGPD — direito à informação). Materializada do
 * ROPA (docs/lgpd/ropa.md).
 */
public record ProcessingActivityView(
    String purpose,
    String legalBasis,
    List<String> dataCategories,
    String retention,
    List<String> operators,
    boolean revocable) {}
