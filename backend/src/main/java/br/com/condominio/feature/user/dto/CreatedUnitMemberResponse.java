package br.com.condominio.feature.user.dto;

import java.util.UUID;

/** Resposta do cadastro de morador: senha provisória mostrada uma única vez. */
public record CreatedUnitMemberResponse(UUID id, String fullName, String password) {

  /** Não vaza a senha em log/exceção; ela só deve trafegar no corpo da resposta. */
  @Override
  public String toString() {
    return "CreatedUnitMemberResponse[id=" + id + ", password=***]";
  }
}
