package br.com.condominio.feature.unit;

import br.com.condominio.feature.unit.dto.UnitLookupResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitService {

  private final UnitRepository repo;

  @Transactional(readOnly = true)
  public Optional<UnitLookupResponse> lookupByCode(String code) {
    return repo.findByCode(code)
        .map(u -> new UnitLookupResponse(u.getId(), u.getCode(), u.getMasterUserId() != null));
  }
}
