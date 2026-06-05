package br.com.condominio.feature.classified;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
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
public class ClassifiedService {

  private final ClassifiedRepository repo;
  private final ClassifiedPhotoRepository photoRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;

  @Transactional
  public ClassifiedView create(UUID authorId, CreateClassifiedRequest req) {
    Classified c = Classified.create(authorId, req.title(), req.description(), req.price());
    repo.save(c);
    return view(c);
  }

  public ClassifiedView getById(UUID id) {
    return view(load(id));
  }

  public Page<ClassifiedView> list(ClassifiedStatus status, Pageable pageable) {
    Page<Classified> page =
        status == null
            ? repo.findByStatus(ClassifiedStatus.ACTIVE, pageable)
            : repo.findByStatus(status, pageable);
    return page.map(this::view);
  }

  @Transactional
  public ClassifiedView update(
      UUID id, UUID actorId, boolean canModerate, UpdateClassifiedRequest req) {
    Classified c = loadOwned(id, actorId, canModerate);
    c.edit(req.title(), req.description(), req.price());
    if (req.status() != null && req.status() != c.getStatus()) {
      applyStatus(c, req.status());
    }
    repo.save(c);
    return view(c);
  }

  @Transactional
  public void delete(UUID id, UUID actorId, boolean canModerate) {
    Classified c = loadOwned(id, actorId, canModerate);
    photoRepo.findByClassifiedIdOrderByOrdering(id).forEach(photoRepo::delete);
    repo.delete(c);
  }

  private static final long MAX_PHOTO_BYTES = 1_048_576L;
  private static final int MAX_PHOTOS = 5;

  /** NÃO transacional: upload pro MinIO acontece fora de transação (CLAUDE.md). */
  public ClassifiedPhotoView addPhoto(
      UUID id, UUID actorId, boolean canModerate, MultipartFile file) {
    loadOwned(id, actorId, canModerate);
    if (photoRepo.countByClassifiedId(id) >= MAX_PHOTOS) {
      throw new ClassifiedException("PHOTO_LIMIT", "Máximo de 5 fotos por anúncio.");
    }
    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = magicBytes.detect(in);
    } catch (IOException e) {
      throw new ClassifiedException("PHOTO_READ_FAILED", "Falha ao ler a imagem.");
    }
    if (!magicBytes.isAcceptedForPhoto(mime)) {
      throw new ClassifiedException("PHOTO_TYPE_INVALID", "Aceitamos JPG, PNG ou WEBP.");
    }
    if (file.getSize() > MAX_PHOTO_BYTES) {
      throw new ClassifiedException("PHOTO_TOO_LARGE", "Foto deve ter no máximo 1MB.");
    }
    String objectKey;
    try (InputStream in = file.getInputStream()) {
      objectKey = storage.upload(props.getBucketClassifieds(), in, file.getSize(), mime);
    } catch (IOException e) {
      throw new ClassifiedException("PHOTO_UPLOAD_FAILED", "Falha ao enviar a imagem.");
    }
    // Trade-off aceito p/ escala de condomínio: a checagem de limite (countByClassifiedId)
    // e o save não são atômicos (TOCTOU) e, se o save falhar após o upload, o objeto fica
    // órfão no bucket. O índice único parcial (classified_id, ordering) impede linha duplicada.
    int ordering = photoRepo.maxOrdering(id) + 1;
    ClassifiedPhoto saved = photoRepo.save(ClassifiedPhoto.create(id, objectKey, mime, ordering));
    return new ClassifiedPhotoView(saved.getId(), saved.getOrdering(), saved.getContentType());
  }

  @Transactional
  public void removePhoto(UUID id, UUID photoId, UUID actorId, boolean canModerate) {
    loadOwned(id, actorId, canModerate);
    ClassifiedPhoto p =
        photoRepo
            .findByIdAndClassifiedId(photoId, id)
            .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Foto não encontrada."));
    photoRepo.delete(p);
  }

  public String photoUrl(UUID id, UUID photoId) {
    load(id);
    ClassifiedPhoto p =
        photoRepo
            .findByIdAndClassifiedId(photoId, id)
            .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Foto não encontrada."));
    return storage.presignedGetUrl(
        props.getBucketClassifieds(),
        p.getObjectKey(),
        Duration.ofSeconds(props.getPresignedTtlPhotosSeconds()));
  }

  private void applyStatus(Classified c, ClassifiedStatus target) {
    switch (target) {
      case SOLD -> c.markSold();
      case ARCHIVED -> c.archive();
      case ACTIVE -> c.reactivate();
    }
  }

  private Classified load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ClassifiedException("NOT_FOUND", "Anúncio não encontrado."));
  }

  private Classified loadOwned(UUID id, UUID actorId, boolean canModerate) {
    Classified c = load(id);
    if (!c.getAuthorUserId().equals(actorId) && !canModerate) {
      throw new ClassifiedException("FORBIDDEN", "Sem permissão sobre este anúncio.");
    }
    return c;
  }

  private ClassifiedView view(Classified c) {
    List<ClassifiedPhotoView> photos =
        photoRepo.findByClassifiedIdOrderByOrdering(c.getId()).stream()
            .map(p -> new ClassifiedPhotoView(p.getId(), p.getOrdering(), p.getContentType()))
            .toList();
    return ClassifiedView.of(c, photos);
  }
}
