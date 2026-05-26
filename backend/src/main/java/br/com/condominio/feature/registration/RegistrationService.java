package br.com.condominio.feature.registration;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.feature.role.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

  private final UnitRepository unitRepo;
  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final RoleRepository roleRepo;
  private final UserRoleRepository userRoleRepo;
  private final ConsentDocumentRepository consentRepo;
  private final FileStorage storage;
  private final MagicBytesValidator magicBytes;
  private final PasswordEncoder encoder;
  private final MinioProperties props;

  @Transactional
  public RegistrationStatusResponse registerMaster(
      RegisterMasterRequest req, MultipartFile proof, String clientIp) {

    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new RegistrationException("EMAIL_TAKEN", "Este e-mail já está cadastrado.");
    }

    String detectedMime;
    try {
      detectedMime = magicBytes.detect(proof.getInputStream());
    } catch (IOException e) {
      throw new RegistrationException("PROOF_READ_FAILED", "Falha ao ler comprovante.");
    }

    if (!magicBytes.isAcceptedForProof(detectedMime)) {
      throw new RegistrationException(
          "PROOF_TYPE_INVALID", "Tipo de comprovante inválido. Aceitamos PDF, JPG, PNG ou WEBP.");
    }

    Unit unit =
        unitRepo
            .findByCode(req.unitCode())
            .orElseThrow(
                () -> new RegistrationException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    if (unit.getMasterUserId() != null) {
      throw new RegistrationException("UNIT_HAS_MASTER", "Esta unidade já possui um master ativo.");
    }

    ConsentDocument consent =
        consentRepo
            .findByVersion(req.consentVersion())
            .orElseThrow(
                () ->
                    new RegistrationException(
                        "CONSENT_VERSION_INVALID", "Versão do termo de privacidade inválida."));

    String objectKey;
    try {
      objectKey =
          storage.upload(
              props.getBucketProofs(), proof.getInputStream(), proof.getSize(), detectedMime);
    } catch (IOException e) {
      throw new RegistrationException("PROOF_UPLOAD_FAILED", "Falha ao enviar comprovante.");
    }

    Role residentRole =
        roleRepo
            .findByName(RoleName.RESIDENT)
            .orElseThrow(() -> new IllegalStateException("RESIDENT role missing"));

    User user = newInstance(User.class);
    setUserFields(
        user, req, unit, objectKey, proof.getOriginalFilename(), detectedMime, consent, clientIp);
    user = userRepo.save(user);

    UserEmail userEmail = newInstance(UserEmail.class);
    setEmail(userEmail, user.getId(), req.email());
    emailRepo.save(userEmail);

    UserRole userRole =
        new UserRole(new UserRoleId(user.getId(), residentRole.getId()), Instant.now(), null);
    userRoleRepo.save(userRole);

    log.info(
        "Master registered: userId={} unitCode={} ip={}", user.getId(), unit.getCode(), clientIp);

    return new RegistrationStatusResponse(user.getId(), user.getStatus().name());
  }

  private void setUserFields(
      User user,
      RegisterMasterRequest req,
      Unit unit,
      String objectKey,
      String originalFilename,
      String contentType,
      ConsentDocument consent,
      String clientIp) {
    try {
      setField(user, "unitId", unit.getId());
      setField(user, "isUnitMaster", true);
      setField(user, "fullName", req.fullName());
      setField(user, "greetingName", req.greetingName());
      setField(user, "phone", req.phone());
      if (req.gender() != null && !req.gender().isBlank()) {
        setField(user, "gender", Gender.valueOf(req.gender()));
      }
      setField(user, "birthDate", req.birthDate());
      setField(user, "passwordHash", encoder.encode(req.password()));
      setField(user, "passwordPepperVersion", (short) 1);
      setField(user, "mustChangePassword", false);
      setField(user, "status", UserStatus.PENDING_APPROVAL);
      setField(user, "residenceProofObjectKey", objectKey);
      setField(user, "residenceProofFilename", originalFilename);
      setField(user, "residenceProofContentType", contentType);
      setField(user, "residenceProofUploadedAt", Instant.now());
      setField(user, "consentDocumentVersion", consent.getVersion());
      setField(user, "consentAcceptedAt", Instant.now());
      setField(user, "consentAcceptedIp", clientIp);
      setField(user, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(user, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException("Failed setting User fields", e);
    }
  }

  private void setEmail(UserEmail e, java.util.UUID userId, String email) {
    try {
      setField(e, "userId", userId);
      setField(e, "email", email);
      setField(e, "isPrimary", true);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static <T> T newInstance(Class<T> clazz) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate " + clazz.getSimpleName(), e);
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Class<?> c = target.getClass();
    while (c != null) {
      try {
        var f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException ex) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
