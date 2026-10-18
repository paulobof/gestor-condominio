package br.com.condominio.feature.complaint.batch.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplaintBatchView(
    UUID id, Instant sentAt, String sentByName, int complaintCount, String renderedText) {}
