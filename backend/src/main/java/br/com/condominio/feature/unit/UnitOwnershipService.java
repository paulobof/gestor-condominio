package br.com.condominio.feature.unit;

import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.dto.OwnershipClaimView;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.user.UserStatus;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Posse de unidade (proprietário): abertura de claim PENDING, aprovação e rejeição. Fica atrás da
 * flag {@code app.feature.unitownership.enabled} via os controllers que o usam.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitOwnershipService {

  private final UnitOwnershipRepository ownershipRepo;
  private final UnitRepository unitRepo;
  private final UserRepository userRepo;
  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final MinioProperties props;

  /**
   * Abre uma posse PENDING para o par usuário+unidade, com o comprovante anexado. Falha se a
   * unidade não existe, já tem master (posse APPROVED) ou já há claim aberto do mesmo par.
   */
  @Transactional
  public UUID openClaim(
      UUID userId, UUID unitId, String proofKey, String proofFilename, String proofContentType) {
    unitRepo
        .findById(unitId)
        .orElseThrow(() -> new UnitOwnershipException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    if (ownershipRepo.existsByUnitIdAndStatus(unitId, OwnershipStatus.APPROVED)) {
      throw new UnitOwnershipException("UNIT_HAS_OWNER", "Esta unidade já possui um proprietário.");
    }

    if (ownershipRepo.existsByUserIdAndUnitIdAndStatusIn(
        userId, unitId, List.of(OwnershipStatus.PENDING, OwnershipStatus.APPROVED))) {
      throw new UnitOwnershipException(
          "DUPLICATE_CLAIM", "Já existe um pedido aberto para esta unidade.");
    }

    UnitOwnership o =
        UnitOwnership.pending(userId, unitId, proofKey, proofFilename, proofContentType);
    UUID id = ownershipRepo.save(o).getId();
    log.info("Ownership claim opened: ownershipId={} unitId={}", id, unitId);
    return id;
  }

  /**
   * Registra a posse de uma unidade extra para o usuário logado: valida o comprovante
   * (magic-bytes), resolve o código da unidade, faz upload e abre o claim PENDING. Não mexe em
   * {@code User.unitId}.
   */
  @Transactional
  public UUID claimExtraUnit(UUID userId, String unitCode, MultipartFile proof) {
    String mime;
    try {
      mime = magicBytes.detect(proof.getInputStream());
    } catch (IOException e) {
      throw new UnitOwnershipException("PROOF_READ_FAILED", "Falha ao ler comprovante.");
    }
    if (!magicBytes.isAcceptedForProof(mime)) {
      throw new UnitOwnershipException(
          "PROOF_TYPE_INVALID", "Tipo de comprovante inválido. Aceitamos PDF, JPG, PNG ou WEBP.");
    }
    Unit unit =
        unitRepo
            .findByCode(unitCode)
            .orElseThrow(
                () -> new UnitOwnershipException("UNIT_NOT_FOUND", "Unidade não encontrada."));
    String objectKey;
    try {
      objectKey =
          storage.upload(props.getBucketProofs(), proof.getInputStream(), proof.getSize(), mime);
    } catch (IOException e) {
      throw new UnitOwnershipException("PROOF_UPLOAD_FAILED", "Falha ao enviar comprovante.");
    }
    return openClaim(userId, unit.getId(), objectKey, proof.getOriginalFilename(), mime);
  }

  /**
   * Aprova uma posse PENDING: marca APPROVED, concede o papel PROPRIETARIO (idempotente) e ativa a
   * conta se for a 1ª posse e o usuário ainda estiver PENDING_APPROVAL. NÃO atribui master à
   * unidade nem concede RESIDENT_MANAGE — posse != mastership.
   */
  @Transactional
  public void approve(UUID ownershipId, UUID approverId) {
    UnitOwnership o =
        ownershipRepo
            .findByIdAndStatus(ownershipId, OwnershipStatus.PENDING)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));

    boolean firstApproved = ownershipRepo.findApprovedUnitIdsByUser(o.getUserId()).isEmpty();
    o.approve(approverId);

    // Posse != mastership: concede o papel PROPRIETARIO (read-only); NÃO atribui master
    // nem RESIDENT_MANAGE.
    grantProprietarioRole(o.getUserId(), approverId);

    if (firstApproved) {
      userRepo
          .findById(o.getUserId())
          .filter(u -> u.getStatus() == UserStatus.PENDING_APPROVAL)
          .ifPresent(u -> u.approveAsOwner(approverId));
    }

    log.info("Ownership approved: ownershipId={} by approverId={}", ownershipId, approverId);
  }

  /** Concede o papel PROPRIETARIO ao usuário, idempotente. */
  private void grantProprietarioRole(UUID userId, UUID approverId) {
    Short roleId =
        roleRepo
            .findByName(RoleName.PROPRIETARIO)
            .orElseThrow(() -> new IllegalStateException("PROPRIETARIO role missing"))
            .getId();
    UserRoleId id = new UserRoleId(userId, roleId);
    if (!userRoleRepo.existsById(id)) {
      userRoleRepo.save(new UserRole(id, Instant.now(), approverId));
    }
  }

  /** Rejeita uma posse PENDING, registra o motivo e purga o comprovante do storage. */
  @Transactional
  public void reject(UUID ownershipId, UUID approverId, String reason) {
    UnitOwnership o =
        ownershipRepo
            .findByIdAndStatus(ownershipId, OwnershipStatus.PENDING)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));
    o.reject(approverId, reason);
    if (o.getResidenceProofObjectKey() != null) {
      try {
        storage.delete(props.getBucketProofs(), o.getResidenceProofObjectKey());
      } catch (Exception e) {
        log.warn(
            "Failed to delete proof for rejected ownership {}: {}", ownershipId, e.getMessage());
      }
    }
    log.info("Ownership rejected: ownershipId={} by approverId={}", ownershipId, approverId);
  }

  /**
   * Lista os pedidos de posse PENDING (mais antigos primeiro), com nome do usuário e código da
   * unidade.
   */
  @Transactional
  public Page<OwnershipClaimView> listPendingClaims(Pageable pageable) {
    return ownershipRepo
        .findByStatusOrderByCreatedAtAsc(OwnershipStatus.PENDING, pageable)
        .map(
            o -> {
              String userName =
                  userRepo.findById(o.getUserId()).map(User::getFullName).orElse(null);
              String unitCode = unitRepo.findById(o.getUnitId()).map(Unit::getCode).orElse(null);
              return new OwnershipClaimView(
                  o.getId(),
                  o.getUserId(),
                  userName,
                  o.getUnitId(),
                  unitCode,
                  o.getResidenceProofFilename(),
                  o.getResidenceProofUploadedAt(),
                  o.getCreatedAt());
            });
  }

  /** Conteúdo do comprovante de um claim para streaming direto pelo backend (MinIO privado). */
  @Transactional
  public ProofContent getClaimProofContent(UUID ownershipId) {
    UnitOwnership o =
        ownershipRepo
            .findById(ownershipId)
            .orElseThrow(
                () -> new UnitOwnershipException("CLAIM_NOT_FOUND", "Pedido não encontrado."));
    if (o.getResidenceProofObjectKey() == null) {
      throw new UnitOwnershipException("NO_PROOF", "Pedido sem comprovante.");
    }
    byte[] content = storage.getObject(props.getBucketProofs(), o.getResidenceProofObjectKey());
    return new ProofContent(
        content, o.getResidenceProofContentType(), o.getResidenceProofFilename());
  }

  public record ProofContent(byte[] content, String contentType, String filename) {}
}
