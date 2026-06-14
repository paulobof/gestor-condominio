package br.com.condominio.feature.parkingrental;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.whatsapp.PhoneNumberNormalizer;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingRentalService {

  private final ParkingRentalRepository repo;
  private final UserRepository userRepo;
  private final PhoneNumberNormalizer normalizer;

  @Transactional
  public ParkingRentalView create(UUID authorId, CreateParkingRentalRequest req) {
    ParkingRental r =
        ParkingRental.create(
            authorId, req.tower(), req.floor(), req.spotNumber(), req.monthlyPrice());
    repo.save(r);
    return view(r);
  }

  public ParkingRentalView getById(UUID id) {
    return view(load(id));
  }

  public Page<ParkingRentalView> list(ParkingRentalStatus status, Pageable pageable) {
    Page<ParkingRental> page =
        status == null
            ? repo.findByStatus(ParkingRentalStatus.ACTIVE, pageable)
            : repo.findByStatus(status, pageable);

    Set<UUID> authorIds =
        page.getContent().stream().map(ParkingRental::getAuthorUserId).collect(Collectors.toSet());
    Map<UUID, User> userIndex =
        userRepo.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    return page.map(
        r -> {
          User u = userIndex.get(r.getAuthorUserId());
          String name = u != null ? u.getFullName() : null;
          String phone = u != null ? u.getPhone() : null;
          return ParkingRentalView.of(r, name, phone, whatsapp(phone));
        });
  }

  @Transactional
  public ParkingRentalView update(
      UUID id, UUID actorId, boolean canModerate, UpdateParkingRentalRequest req) {
    ParkingRental r = loadOwned(id, actorId, canModerate);
    r.edit(req.tower(), req.floor(), req.spotNumber(), req.monthlyPrice());
    if (req.status() != null && req.status() != r.getStatus()) {
      applyStatus(r, req.status());
    }
    repo.save(r);
    return view(r);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    repo.delete(loadOwned(id, actorId, canModerate));
  }

  private void applyStatus(ParkingRental r, ParkingRentalStatus target) {
    switch (target) {
      case RENTED -> r.markRented();
      case ARCHIVED -> r.archive();
      case ACTIVE -> r.reactivate();
    }
  }

  private ParkingRental load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ParkingRentalException("NOT_FOUND", "Anúncio não encontrado."));
  }

  private ParkingRental loadOwned(UUID id, UUID actorId, boolean canModerate) {
    ParkingRental r = load(id);
    if (!r.getAuthorUserId().equals(actorId) && !canModerate) {
      throw new ParkingRentalException("FORBIDDEN", "Sem permissão sobre este anúncio.");
    }
    return r;
  }

  /** Resolve o autor (1 query) e monta a view com contato + número de WhatsApp normalizado. */
  private ParkingRentalView view(ParkingRental r) {
    User author =
        userRepo.findAllById(List.of(r.getAuthorUserId())).stream().findFirst().orElse(null);
    String name = author != null ? author.getFullName() : null;
    String phone = author != null ? author.getPhone() : null;
    return ParkingRentalView.of(r, name, phone, whatsapp(phone));
  }

  /** Número pronto para wa.me (DDI), ou null se ausente/inválido. Nunca propaga PII em log. */
  private String whatsapp(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    try {
      return normalizer.toEvolutionNumber(phone);
    } catch (RuntimeException e) {
      log.debug("Telefone do autor não normalizável para WhatsApp; botão será omitido.");
      return null;
    }
  }
}
