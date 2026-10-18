package br.com.condominio.feature.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominio.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ComplaintExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @ParameterizedTest
  @CsvSource({
    "NOT_FOUND,404",
    "FORBIDDEN,403",
    "NO_UNIT,403",
    "INVALID_STATE,409",
    "DUPLICATE_NAME,409",
    "BATCH_STALE,409",
    "CATALOG_INVALID,422",
    "INVALID_INPUT,400",
    "DUE_DATE_PAST,400",
    "PHOTO_LIMIT,400"
  })
  void mapsCodeToStatus(String code, int expected) {
    var response = handler.handleComplaint(new ComplaintException(code, "msg"));
    assertThat(response.getStatusCode().value()).isEqualTo(expected);
    assertThat(response.getBody().code()).isEqualTo(code);
  }
}
