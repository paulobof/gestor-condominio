package br.com.condominio.feature.complaint;

import java.util.UUID;

/** Mudança registrada pelo agregado; vira {@link ComplaintEvent} após o save. Sem PII. */
public record ComplaintChange(
    ComplaintEventKind kind, UUID actorId, String fromValue, String toValue) {}
