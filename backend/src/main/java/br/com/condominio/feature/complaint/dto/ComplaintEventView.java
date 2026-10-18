package br.com.condominio.feature.complaint.dto;

import br.com.condominio.feature.complaint.ComplaintEventKind;
import java.time.Instant;

public record ComplaintEventView(
    ComplaintEventKind kind,
    String actorName,
    String fromValue,
    String toValue,
    Instant createdAt) {}
