package br.com.condominio.feature.complaint.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplaintBatchTextRendererTest {

  private final ComplaintBatchTextRenderer renderer = new ComplaintBatchTextRenderer();
  private final LocalDate date = LocalDate.of(2026, 10, 17);

  @Test
  void groupsByTypeOrder_numbersContinuously_andFlattensDescription() {
    List<BatchLine> lines =
        List.of(
            new BatchLine(130, "Limpeza", 2, "Garagem", "Garagem", null, "Óleo no piso"),
            new BatchLine(
                127,
                "Manutenção",
                1,
                "Elétrica",
                "Garagem",
                null,
                "Lâmpada queimada\n\n  na vaga   G1-045"),
            new BatchLine(
                123,
                "Manutenção",
                1,
                "Pintura",
                "Torre 2",
                "hall do 5º andar",
                "  Parede descascando.  "));

    String text = renderer.render(date, lines);

    assertThat(text)
        .isEqualTo(
            """
            Reclamações — HELBOR TRILOGY HOME
            Lote de 17/10/2026 (3 reclamações)

            MANUTENÇÃO
            1. #123 Pintura — Torre 2 (hall do 5º andar)
               Parede descascando.
            2. #127 Elétrica — Garagem
               Lâmpada queimada na vaga G1-045

            LIMPEZA
            3. #130 Garagem — Garagem
               Óleo no piso""");
  }

  @Test
  void flattensLocationDetailToOneLine() {
    String detail = String.join(System.lineSeparator(), "hall", "  do 5º", "andar");
    String text =
        renderer.render(
            date,
            List.of(new BatchLine(1, "Outros", 5, "Outros", "Torre 1", detail, "Interfone mudo")));
    assertThat(text).contains("Torre 1 (hall do 5º andar)");
  }

  @Test
  void singular_whenOneComplaint() {
    String text =
        renderer.render(
            date,
            List.of(new BatchLine(1, "Outros", 5, "Outros", "Portaria", null, "Interfone mudo")));
    assertThat(text).contains("(1 reclamação)");
  }
}
