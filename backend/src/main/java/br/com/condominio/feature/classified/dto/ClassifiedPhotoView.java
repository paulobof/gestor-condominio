package br.com.condominio.feature.classified.dto;

import java.util.UUID;

public record ClassifiedPhotoView(UUID id, int ordering, String contentType) {}
