package br.com.condominio.feature.consent;

import br.com.condominio.feature.consent.dto.ConsentDocumentView;
import br.com.condominio.feature.registration.ConsentDocumentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

  private final ConsentDocumentRepository repo;

  @Transactional(readOnly = true)
  public Optional<ConsentDocumentView> current() {
    return repo.findLatest()
        .map(d -> new ConsentDocumentView(d.getVersion(), d.getBody(), d.getPublishedAt()));
  }
}
