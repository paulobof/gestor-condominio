package br.com.condominio.feature.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title, @NotBlank @Size(max = 5000) String body) {}
