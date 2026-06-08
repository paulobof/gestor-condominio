package br.com.condominio.feature.user.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateUnitMemberRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @NotBlank @StrongPassword String password,
    boolean whatsappOptIn) {}
