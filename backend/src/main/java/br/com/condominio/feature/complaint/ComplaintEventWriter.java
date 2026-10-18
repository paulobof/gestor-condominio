package br.com.condominio.feature.complaint;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persiste as mudanças acumuladas no agregado. Chamar depois do save (precisa do id). */
@Component
@RequiredArgsConstructor
public class ComplaintEventWriter {

  private final ComplaintEventRepository events;

  public void flush(Complaint c) {
    var pending = c.drainChanges();
    if (pending.isEmpty()) return;
    events.saveAll(pending.stream().map(ch -> ComplaintEvent.of(c.getId(), ch)).toList());
  }

  public void record(UUID complaintId, ComplaintChange change) {
    events.save(ComplaintEvent.of(complaintId, change));
  }
}
