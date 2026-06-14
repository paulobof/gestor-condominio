package br.com.condominio.feature.recommendation;

import br.com.condominio.feature.recommendation.dto.*;
import br.com.condominio.feature.tag.Tag;
import br.com.condominio.feature.tag.TagService;
import br.com.condominio.feature.tag.dto.TagView;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private final RecommendationRepository repo;
  private final RecommendationPhotoRepository photoRepo;
  private final RecommendationOpeningHoursRepository hoursRepo;
  private final TagService tagService;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;
  private final UserRepository userRepo;
  private final UnitRepository unitRepo;

  @Transactional
  public RecommendationView create(
      UUID authorId, UUID authorUnitId, boolean authorIsResident, CreateRecommendationRequest req) {
    UUID ownerUnitId = null;
    String ownerUnitCode = null;
    if (req.isResident()) {
      if (req.residentUserId() == null) {
        // "sou eu" path: morador cadastrando a si próprio
        if (authorUnitId == null) {
          throw new RecommendationException(
              "OWNER_UNIT_REQUIRED",
              "Usuário sem unidade não pode marcar 'esta indicação é minha'.");
        }
        ownerUnitId = authorUnitId;
        ownerUnitCode = unitRepo.findCodeById(authorUnitId).orElse(null);
      } else {
        // admin indica em nome de morador
        ownerUnitId =
            userRepo
                .findUnitIdById(req.residentUserId())
                .orElseThrow(
                    () ->
                        new RecommendationException(
                            "RESIDENT_NOT_FOUND", "Morador não encontrado ou sem unidade."));
        ownerUnitCode = unitRepo.findCodeById(ownerUnitId).orElse(null);
      }
    }

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
            req.comment(),
            req.instagramUrl(),
            req.facebookUrl(),
            req.whatsappUrl(),
            req.catalogUrl(),
            ownerUnitId,
            ownerUnitCode);
    applyTags(r, req.tagSlugs());
    repo.save(r);
    replaceHours(r.getId(), req.openingHours());
    return view(r);
  }

  // @Transactional nas leituras: view() acessa r.getTags() (@ManyToMany lazy); sem sessão aberta
  // daria LazyInitializationException.
  @Transactional
  public RecommendationView getById(UUID id, UUID actorId, boolean canModerate) {
    Recommendation r = load(id);
    // Indicação HIDDEN (oculta por moderação) só é visível ao autor, ao morador indicado ou a um
    // moderador. Para os demais, NOT_FOUND (não confirma existência).
    if (r.getStatus() != RecommendationStatus.ACTIVE
        && !canModerate
        && !actorId.equals(r.getRecommendedByUserId())
        && !actorId.equals(r.getResidentUserId())) {
      throw new RecommendationException("NOT_FOUND", "Indicação não encontrada.");
    }
    return view(r);
  }

  @Transactional
  public Page<RecommendationView> list(
      String tag, boolean residentOnly, String search, Pageable pageable) {
    String t = (tag == null || tag.isBlank()) ? null : tag;
    String s = (search == null || search.isBlank()) ? null : search;
    return repo.search(t, residentOnly, s, pageable).map(this::view);
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
        req.comment(),
        req.instagramUrl(),
        req.facebookUrl(),
        req.whatsappUrl(),
        req.catalogUrl());
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

  private static final long MAX_PHOTO_BYTES = 1_048_576L;
  private static final int MAX_PHOTOS = 5;

  /** NÃO transacional: upload pro MinIO acontece fora de transação (CLAUDE.md). */
  public RecommendationPhotoView addPhoto(
      UUID id, UUID actorId, boolean canModerate, MultipartFile file) {
    loadOwned(id, actorId, canModerate);
    if (photoRepo.countByRecommendationId(id) >= MAX_PHOTOS) {
      throw new RecommendationException("PHOTO_LIMIT", "Máximo de 5 fotos por indicação.");
    }
    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = magicBytes.detect(in);
    } catch (IOException e) {
      throw new RecommendationException("PHOTO_READ_FAILED", "Falha ao ler a imagem.");
    }
    if (!magicBytes.isAcceptedForPhoto(mime)) {
      throw new RecommendationException("PHOTO_TYPE_INVALID", "Aceitamos JPG, PNG ou WEBP.");
    }
    if (file.getSize() > MAX_PHOTO_BYTES) {
      throw new RecommendationException("PHOTO_TOO_LARGE", "Foto deve ter no máximo 1MB.");
    }
    String objectKey;
    try (InputStream in = file.getInputStream()) {
      objectKey = storage.upload(props.getBucketRecommendations(), in, file.getSize(), mime);
    } catch (IOException e) {
      throw new RecommendationException("PHOTO_UPLOAD_FAILED", "Falha ao enviar a imagem.");
    }
    // Trade-off aceito p/ escala de condomínio: checagem de limite e save não são atômicos
    // (TOCTOU) e, se o save falhar após o upload, o objeto fica órfão no bucket. O índice
    // único parcial (recommendation_id, ordering) impede linha duplicada.
    int ordering = photoRepo.maxOrdering(id) + 1;
    RecommendationPhoto saved =
        photoRepo.save(RecommendationPhoto.create(id, objectKey, mime, ordering));
    return new RecommendationPhotoView(saved.getId(), saved.getOrdering(), saved.getContentType());
  }

  @Transactional
  public void removePhoto(UUID id, UUID photoId, UUID actorId, boolean canModerate) {
    loadOwned(id, actorId, canModerate);
    RecommendationPhoto p =
        photoRepo
            .findByIdAndRecommendationId(photoId, id)
            .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Foto não encontrada."));
    photoRepo.delete(p);
  }

  public String photoUrl(UUID id, UUID photoId) {
    load(id);
    RecommendationPhoto p =
        photoRepo
            .findByIdAndRecommendationId(photoId, id)
            .orElseThrow(() -> new RecommendationException("NOT_FOUND", "Foto não encontrada."));
    return storage.presignedGetUrl(
        props.getBucketRecommendations(),
        p.getObjectKey(),
        Duration.ofSeconds(props.getPresignedTtlPhotosSeconds()));
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
