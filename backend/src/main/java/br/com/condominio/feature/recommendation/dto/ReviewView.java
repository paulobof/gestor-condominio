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
 * <p>{@code sameUnit} é a metade dessa decisão que interessa ao cliente: verdadeiro só quando o
 * motivo da revelação foi "mesma unidade", nunca quando foi moderação. Sem isso, o frontend não
 * tinha como distinguir "meu apartamento já avaliou" de "eu modero e por isso vejo o nome de
 * qualquer um" — um morador-moderador cujo apartamento não avaliou via a mensagem errada e perdia o
 * formulário. {@code authorName}/{@code unitCode} continuam cobrindo os dois motivos juntos (é o
 * que a tela mostra); {@code sameUnit} existe só para a tela decidir o que fazer.
 *
 * <p>O anonimato é decidido AQUI, no backend. Nunca mandar o nome e esconder no frontend, e nunca
 * deduzir "mesma unidade" no frontend a partir de authorName — é exatamente esse tipo de dedução
 * que este campo elimina.
 */
public record ReviewView(
    UUID id,
    String sentiment,
    int rating,
    String text,
    Instant createdAt,
    boolean mine,
    String authorName,
    String unitCode,
    boolean sameUnit) {}
