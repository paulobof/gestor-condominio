package br.com.condominio.feature.recommendation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação como o app a exibe. {@code authorName} e {@code unitCode} vêm nulos — a avaliação é
 * anônima entre vizinhos. Duas exceções, e só elas:
 *
 * <ul>
 *   <li>quem tem {@code RECOMMENDATION_MODERATE}, para tratar um abuso com a pessoa em vez de só
 *       ocultar;
 *   <li>quem mora na MESMA unidade da avaliação — é assim que o segundo morador entende por que não
 *       pode avaliar ("seu apartamento já avaliou, por Ana"). Revelação dentro de casa, entre
 *       pessoas que já se conhecem, não é exposição a vizinho.
 * </ul>
 *
 * <p>O anonimato é decidido AQUI, no backend. Nunca mandar o nome e esconder no frontend.
 */
public record ReviewView(
    UUID id,
    String sentiment,
    int rating,
    String text,
    Instant createdAt,
    boolean mine,
    String authorName,
    String unitCode) {}
