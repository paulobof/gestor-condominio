package br.com.condominio.feature.access.dto;

import java.util.UUID;

/** Resposta do cadastro: senha provisória mostrada uma única vez. */
public record CreatedUserResponse(UUID id, String fullName, String password) {

  /** Não vaza a senha em log/exceção; ela só deve trafegar no corpo da resposta. */
  @Override
  public String toString() {
    return "CreatedUserResponse[id=" + id + ", password=***]";
  }
}
