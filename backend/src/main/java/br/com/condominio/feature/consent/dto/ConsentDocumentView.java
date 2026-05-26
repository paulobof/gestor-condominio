package br.com.condominio.feature.consent.dto;

import java.time.Instant;

public record ConsentDocumentView(String version, String body, Instant publishedAt) {}
