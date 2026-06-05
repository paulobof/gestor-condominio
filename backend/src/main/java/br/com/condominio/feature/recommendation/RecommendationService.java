package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.*;
import br.com.condominio.feature.recommendation.event.RecommendationConsentRequestedEvent;
import br.com.condominio.feature.tag.Tag;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.tag.dto.TagView;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private final RecommendationRepository repo;
  private final RecommendationPhotoRepository photoRepo;
  private final RecommendationOpeningHoursRepository hoursRepo;
  private final TagService tagService;
  private final UserRepository userRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;
  private final ApplicationEventPublisher events;

  @Transactional
  public RecommendationView create(UUID authorId, CreateRecommendationRequest req) {
    Recommendation r =
        Recommendation.create(
            authorId,
            req.serviceName(),
            req.professionalName(),
            req.phone(),
            req.isResident(),
            req.residentUserId(),
            req.addressLine(),
            req.priceRange(),
            req.rating(),
            req.comment());
    applyTags(r, req.tagSlugs());
    repo.save(r);
    replaceHours(r.getId(), req.openingHours());
    if (req.isResident()) {
      User resident =
          userRepo
              .findById(req.residentUserId())
              .orElseThrow(
                  () ->
                      new RecommendationException("NOT_FOUND", "Morador indicado não encontrado."));
      events.publishEvent(
          new RecommendationConsentRequestedEvent(
              r.getId(),
              resident.getId(),
              resident.getPhone(),
              resident.getGreetingName(),
              authorDisplay(authorId),
              r.getServiceName()));
    }
    return view(r);
  }

  // @Transactional nas leituras: view() acessa r.getTags() (@ManyToMany lazy); sem sessão aberta
  // daria LazyInitializationException.
  @Transactional
  public RecommendationView getById(UUID id) {
    return view(load(id));
  }

  @Transactional
  public Page<RecommendationView> list(
      String tag, boolean residentOnly, String search, Pageable pageable) {
    String t = (tag == null || tag.isBlank()) ? null : tag;
    String s = (search == null || search.isBlank()) ? null : search;
    return repo.search(t, residentOnly, s, pageable).map(this::view);
  }

  @Transactional
  public List<RecommendationView> pendingConsentFor(UUID residentUserId) {
    return repo
        .findByResidentUserIdAndStatus(
            residentUserId, RecommendationStatus.PENDING_RESIDENT_CONSENT)
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public RecommendationView update(
      UUID id, UUID actorId, boolean canModerate, UpdateRecommendationRequest req) {
    Recommendation r = loadOwned(id, actorId, canModerate);
    r.edit(
        req.serviceName(),
        req.professionalName(),
        req.phone(),
        req.addressLine(),
        req.priceRange(),
        req.rating(),
        req.comment());
    applyTags(r, req.tagSlugs());
    repo.save(r);
    replaceHours(id, req.openingHours());
    return view(r);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = loadOwned(id, actorId, canModerate);
    photoRepo.findByRecommendationIdOrderByOrdering(id).forEach(photoRepo::delete);
    repo.delete(r);
  }

  @Transactional
  public void hide(UUID id) {
    Recommendation r = load(id);
    r.hide();
    repo.save(r);
  }

  @Transactional
  public void residentConsent(UUID id, UUID actorId, boolean canModerate, boolean approved) {
    Recommendation r = load(id);
    boolean isTheResident = actorId.equals(r.getResidentUserId());
    if (!isTheResident && !canModerate) {
      throw new RecommendationException("FORBIDDEN", "Apenas o morador indicado pode responder.");
    }
    if (!r.isPendingConsent()) {
      throw new RecommendationException(
          "INVALID_STATE", "Indicação não está aguardando consentimento.");
    }
    if (approved) {
      r.consentByResident();
      repo.save(r);
    } else {
      repo.delete(r); // recusa = soft delete (direito do titular)
    }
  }

  private void applyTags(Recommendation r, List<String> slugs) {
    Set<Tag> tags = new HashSet<>();
    if (slugs != null) {
      for (String slug : slugs) {
        if (slug != null && !slug.isBlank()) tags.add(tagService.getOrCreate(slug, null));
      }
    }
    r.replaceTags(tags);
  }

  private void replaceHours(UUID recommendationId, List<OpeningHoursDto> hours) {
    hoursRepo.deleteByOwnerId(recommendationId);
    if (hours == null) return;
    for (OpeningHoursDto h : hours) {
      hoursRepo.save(
          RecommendationOpeningHours.create(
              recommendationId, (short) h.dayOfWeek(), h.opensAt(), h.closesAt(), h.notes()));
    }
  }

  private String authorDisplay(UUID authorId) {
    return userRepo.findById(authorId).map(User::getGreetingName).orElse("Um morador");
  }

  private Recommendation load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Indicação não encontrada."));
  }

  private Recommendation loadOwned(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = load(id);
    if (!r.getRecommendedByUserId().equals(actorId) && !canModerate) {
      throw new RecommendationException("FORBIDDEN", "Sem permissão sobre esta indicação.");
    }
    return r;
  }

  private RecommendationView view(Recommendation r) {
    List<TagView> tags = r.getTags().stream().map(TagView::of).toList();
    List<OpeningHoursDto> hours =
        hoursRepo.findByOwnerIdOrderByDayOfWeek(r.getId()).stream()
            .map(
                h ->
                    new OpeningHoursDto(
                        h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.getNotes()))
            .toList();
    List<RecommendationPhotoView> photos =
        photoRepo.findByRecommendationIdOrderByOrdering(r.getId()).stream()
            .map(p -> new RecommendationPhotoView(p.getId(), p.getOrdering(), p.getContentType()))
            .toList();
    return RecommendationView.of(r, tags, hours, photos);
  }
}
