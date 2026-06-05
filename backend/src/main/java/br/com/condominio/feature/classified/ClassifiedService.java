package br.com.condominio.feature.classified;

import br.com.condominio.feature.classified.dto.ClassifiedPhotoView;
import br.com.condominio.feature.classified.dto.ClassifiedView;
import br.com.condominio.feature.classified.dto.CreateClassifiedRequest;
import br.com.condominio.feature.classified.dto.UpdateClassifiedRequest;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
