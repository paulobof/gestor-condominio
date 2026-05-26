package br.com.condominio.feature.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitServiceTest {

  private UnitRepository repo;
  private UnitService service;

  @BeforeEach
  void setUp() {
    repo = mock(UnitRepository.class);
    service = new UnitService(repo);
  }

  @Test
  void lookupReturnsHasMasterTrueWhenAlreadyAssigned() {
    Unit unit = new Unit();
    setField(unit, "code", "702C");
    setField(unit, "masterUserId", UUID.randomUUID());
    setField(unit, "id", UUID.randomUUID());
    when(repo.findByCode("702C")).thenReturn(Optional.of(unit));

    var resp = service.lookupByCode("702C");
    assertThat(resp).isPresent();
    assertThat(resp.get().code()).isEqualTo("702C");
    assertThat(resp.get().hasActiveMaster()).isTrue();
  }

  @Test
  void lookupReturnsHasMasterFalseWhenNotAssigned() {
    Unit unit = new Unit();
    setField(unit, "code", "402A");
    setField(unit, "id", UUID.randomUUID());
    when(repo.findByCode("402A")).thenReturn(Optional.of(unit));
    assertThat(service.lookupByCode("402A").get().hasActiveMaster()).isFalse();
  }

  @Test
  void lookupReturnsEmptyWhenUnknownCode() {
    when(repo.findByCode("999Z")).thenReturn(Optional.empty());
    assertThat(service.lookupByCode("999Z")).isEmpty();
  }

  private static void setField(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
