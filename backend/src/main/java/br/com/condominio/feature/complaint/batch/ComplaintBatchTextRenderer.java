package br.com.condominio.feature.complaint.batch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Texto do lote semanal para a síndica (copiar/colar). Puro: sem I/O, sem relógio. */
@Component
public class ComplaintBatchTextRenderer {

  private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  public String render(LocalDate date, List<BatchLine> lines) {
    List<BatchLine> sorted =
        lines.stream()
            .sorted(
                Comparator.comparingInt(BatchLine::typeOrder)
                    .thenComparing(BatchLine::typeName)
                    .thenComparingLong(BatchLine::code))
            .toList();
    int n = sorted.size();
    StringBuilder sb = new StringBuilder("Chamados — HELBOR TRILOGY HOME\n");
    sb.append("Lote de ")
        .append(BR.format(date))
        .append(" (")
        .append(n)
        .append(n == 1 ? " chamado" : " chamados")
        .append(")\n");
    String currentType = null;
    int i = 0;
    for (BatchLine l : sorted) {
      if (!l.typeName().equals(currentType)) {
        sb.append('\n').append(l.typeName().toUpperCase(PT_BR)).append('\n');
        currentType = l.typeName();
      }
      sb.append(++i)
          .append(". #")
          .append(l.code())
          .append(' ')
          .append(l.subtypeName())
          .append(" — ")
          .append(l.locationName());
      if (l.locationDetail() != null) {
        sb.append(" (").append(oneLine(l.locationDetail())).append(')');
      }
      sb.append("\n   ").append(oneLine(l.description())).append('\n');
    }
    return sb.toString().stripTrailing();
  }

  private static String oneLine(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }
}
