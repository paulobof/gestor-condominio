package br.com.condominio.feature.complaint.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code expectedText}: o texto que o admin viu/copiou no preview. Opcional; quando presente, a
 * criação do lote recusa (409 BATCH_STALE) se o texto re-renderizado for diferente. Limite folgado
 * para 200 chamados com descrição de até 2000 caracteres.
 */
public record BatchRequest(
    @NotEmpty @Size(max = 200) List<@NotNull UUID> ids, @Size(max = 600_000) String expectedText) {}
