package br.com.condominio.feature.recommendation.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentView(
    UUID id, UUID authorUserId, String authorName, String text, Instant createdAt) {}
