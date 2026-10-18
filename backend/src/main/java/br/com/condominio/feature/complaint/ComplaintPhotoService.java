package br.com.condominio.feature.complaint;

import br.com.condominio.feature.complaint.dto.ComplaintPhotoView;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ComplaintPhotoService {

  static final long MAX_PHOTO_BYTES = 1_048_576L;
  static final int MAX_PHOTOS = 3;

  private final ComplaintRepository repo;
  private final ComplaintPhotoRepository photoRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;
  private final ComplaintEventWriter events;

  /**
   * NÃO transacional: o upload pro MinIO acontece fora de transação (CLAUDE.md). Mesmo trade-off de
   * classificados: checagem de limite e save não são atômicos; o índice único parcial
   * (complaint_id, ordering) impede linha duplicada.
   */
  public ComplaintPhotoView addPhoto(UUID id, UUID actor, MultipartFile file) {
    Complaint c = load(id);
    c.requirePhotoEditableBy(actor);
    if (photoRepo.countByComplaintId(id) >= MAX_PHOTOS) {
      throw new ComplaintException("PHOTO_LIMIT", "Máximo de 3 fotos por reclamação.");
    }
    if (file.getSize() > MAX_PHOTO_BYTES) {
      throw new ComplaintException("PHOTO_TOO_LARGE", "Foto deve ter no máximo 1MB.");
    }
    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = magicBytes.detect(in);
    } catch (IOException e) {
      throw new ComplaintException("PHOTO_READ_FAILED", "Falha ao ler a imagem.");
    }
    if (!magicBytes.isAcceptedForPhoto(mime)) {
      throw new ComplaintException("PHOTO_TYPE_INVALID", "Aceitamos JPG, PNG ou WEBP.");
    }
    String objectKey;
    try (InputStream in = file.getInputStream()) {
      objectKey = storage.upload(props.getBucketComplaints(), in, file.getSize(), mime);
    } catch (IOException e) {
      throw new ComplaintException("PHOTO_UPLOAD_FAILED", "Falha ao enviar a imagem.");
    }
    int ordering = photoRepo.maxOrdering(id) + 1;
    ComplaintPhoto saved =
        photoRepo.save(ComplaintPhoto.create(id, objectKey, mime, file.getSize(), ordering));
    events.record(
        id,
        new ComplaintChange(ComplaintEventKind.PHOTO_ADDED, actor, null, saved.getId().toString()));
    return ComplaintPhotoView.of(saved);
  }

  @Transactional
  public void removePhoto(UUID id, UUID photoId, UUID actor) {
    Complaint c = load(id);
    c.requirePhotoEditableBy(actor);
    ComplaintPhoto p =
        photoRepo.findByIdAndComplaintId(photoId, id).orElseThrow(this::photoNotFound);
    photoRepo.delete(p);
    events.record(
        id, new ComplaintChange(ComplaintEventKind.PHOTO_REMOVED, actor, photoId.toString(), null));
  }

  @Transactional(readOnly = true)
  public String photoUrl(UUID id, UUID photoId, UUID actor, UUID unitId, boolean canManage) {
    ComplaintService.requireVisible(repo.findById(id), actor, unitId, canManage);
    ComplaintPhoto p =
        photoRepo.findByIdAndComplaintId(photoId, id).orElseThrow(this::photoNotFound);
    return storage.presignedGetUrl(
        props.getBucketComplaints(),
        p.getObjectKey(),
        Duration.ofSeconds(props.getPresignedTtlPhotosSeconds()));
  }

  private Complaint load(UUID id) {
    return repo.findById(id).orElseThrow(ComplaintService::notFound);
  }

  private ComplaintException photoNotFound() {
    return new ComplaintException("NOT_FOUND", "Foto não encontrada.");
  }
}
