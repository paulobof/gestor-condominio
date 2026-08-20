package br.com.condominio.feature.registration.dto;

import br.com.condominio.shared.validation.StrongPassword;
import jakarta.validation.constraints.*;

/**
 * Cadastro do morador. Pede o mínimo para entrar: quem é, onde mora e como falar com ele. Sem
 * comprovante de residência — quem chega primeiro na unidade vira master; os próximos entram como
 * pedido aprovado por ele (ver {@code RegistrationService#registerMaster}).
 */
public record RegisterMasterRequest(
    @NotBlank @Size(max = 180) String fullName,
    @NotBlank @Size(max = 60) String greetingName,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
    @NotBlank String unitCode,
    @NotBlank @StrongPassword String password,
    @NotBlank String consentVersion,
    boolean whatsappOptIn) {}
