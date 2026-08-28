package br.com.condominio.feature.registration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Convidado e Proprietário saíram do produto (2026-08-27): as rotas de criação de conta desses
 * tipos não podem mais existir.
 *
 * <p>Guarda <b>estrutural</b>, e não de status HTTP, de propósito. Um {@code @WebMvcTest} fatiado
 * registra apenas o controller nomeado, então responderia 404 para essas rotas mesmo com o
 * controller ainda no código — o teste passaria de imediato e não provaria nada. Sem a classe não
 * há mapeamento possível, e é isso que se verifica aqui.
 */
class RegistrationClosedRoutesTest {

  @Test
  void naoExisteMaisControllerDeProprietario() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("br.com.condominio.feature.registration.RegisterOwnerController"));
  }

  @Test
  void naoExisteMaisControllerDeConvidado() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("br.com.condominio.feature.registration.RegisterGuestController"));
  }
}
