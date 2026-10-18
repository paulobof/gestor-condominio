package br.com.condominio.feature.complaint.dto;

import java.util.List;

public record ComplaintAdminDetailView(
    ComplaintView complaint, String authorName, String unitCode, List<ComplaintEventView> events) {}
