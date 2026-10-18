package br.com.condominio.feature.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Campos editáveis de um chamado: corpo de criar/editar (morador e admin). */
public record ComplaintFields(
    @NotNull UUID typeId,
    @NotNull UUID subtypeId,
    @NotNull UUID locationId,
    @Size(max = 200) String locationDetail,
    @NotBlank @Size(max = 2000) String description) {}
