package br.com.condominio.feature.activity;

import java.util.UUID;

/**
 * Evento de atividade (criação/edição/exclusão de conteúdo) publicado pelos services. O {@code
 * label} é um identificador do item SEM PII (ex.: título da indicação, código da unidade) — nunca
 * nome/e-mail/telefone. {@code actorUserId} serve só para resolver o papel (role) do autor, não o
 * nome.
 */
public record ActivityEvent(
    ActivityAction action, String entityType, String label, UUID actorUserId) {}
