package br.com.condominio.feature.user.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/** Troca de senha pelo próprio usuário: exige a senha atual; a nova segue a política forte. */
public record ChangeOwnPasswordRequest(
    @NotBlank String currentPassword, @NotBlank @StrongPassword String newPassword) {}
