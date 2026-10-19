package br.com.condominio.feature.user.dto;

import br.com.condominio.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edição dos próprios dados. Saudação e telefone são opcionais (em branco = remover). Telefone, se
 * informado, segue {@link ValidationPatterns#PHONE}.
 */
public record UpdateOwnProfileRequest(
    @NotBlank @Size(max = 180) String fullName,
    @Size(max = 60) String greetingName,
    @Size(max = 20) @Pattern(regexp = "\\s*|" + ValidationPatterns.PHONE) String phone) {}
