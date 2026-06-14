package br.com.condominio.feature.announcement.dto;

import br.com.condominio.feature.announcement.AnnouncementImportance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAnnouncementRequest(
    @NotBlank @Size(max = 140) String title,
    @NotBlank @Size(max = 5000) String body,
    @NotNull AnnouncementImportance importance) {}
