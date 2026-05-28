package br.com.condominio.feature.privacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Confirmação dupla para anonimização (irreversível). {@code currentPassword} prova posse da conta;
 * {@code confirmText} deve ser literalmente "ANONIMIZAR" para evitar disparo acidental.
 */
public record AnonymizeRequest(
    @NotBlank String currentPassword,
    @NotBlank @Pattern(regexp = "ANONIMIZAR", message = "Confirme com a palavra 'ANONIMIZAR'")
        String confirmText) {}
