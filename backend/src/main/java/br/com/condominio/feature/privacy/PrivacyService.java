package br.com.condominio.feature.privacy;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.privacy.dto.PersonalDataExportResponse;
import br.com.condominio.feature.privacy.dto.ProcessingActivityView;
import br.com.condominio.feature.privacy.event.UserAnonymizedEvent;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra os direitos LGPD do titular: exportar, listar atividades de tratamento, alternar opt-in
 * WhatsApp e anonimizar (irreversível).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrivacyService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleRepository roleRepo;
  private final UnitRepository unitRepo;
  private final RefreshTokenRepository refreshTokenRepo;
  private final ProcessingActivitiesProvider activitiesProvider;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public PersonalDataExportResponse exportSelf(UUID userId) {
    User user = userRepo.findById(userId).orElseThrow(() -> notFound(userId));
    List<String> emails = emailRepo.findByUserId(userId).stream().map(UserEmail::getEmail).toList();
    List<String> roles =
        userRoleRepo.findById_UserId(userId).stream()
            .map(ur -> roleRepo.findById(ur.getId().getRoleId()))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .map(Role::getName)
            .map(Enum::name)
            .toList();
    PersonalDataExportResponse.UnitInfo unitInfo =
        user.getUnitId() == null
            ? null
            : unitRepo
                .findById(user.getUnitId())
                .map(
                    u ->
                        new PersonalDataExportResponse.UnitInfo(
                            u.getId(), u.getCode(), user.isUnitMaster()))
                .orElse(null);
    PersonalDataExportResponse.ResidenceProofInfo proofInfo =
        user.getResidenceProofUploadedAt() == null
            ? null
            : new PersonalDataExportResponse.ResidenceProofInfo(
                user.getResidenceProofFilename(),
                user.getResidenceProofContentType(),
                user.getResidenceProofUploadedAt(),
                user.getProofVerifiedAt());
    PersonalDataExportResponse.ConsentInfo consentInfo =
        new PersonalDataExportResponse.ConsentInfo(
            user.getConsentDocumentVersion(), user.getConsentAcceptedAt());
    return new PersonalDataExportResponse(
        user.getId(),
        user.getFullName(),
        user.getGreetingName(),
        emails,
        user.getPhone(),
        user.getPhoneVerifiedAt(),
        user.getGender() == null ? null : user.getGender().name(),
        user.getBirthDate(),
        user.getStatus().name(),
        unitInfo,
        proofInfo,
        consentInfo,
        user.isWhatsappOptIn(),
        user.getWhatsappOptInAt(),
        user.getCreatedAt(),
        user.getUpdatedAt(),
        roles,
        Instant.now());
  }

  @Transactional(readOnly = true)
  public List<ProcessingActivityView> processingActivities() {
    return activitiesProvider.list();
  }

  /**
   * Atualiza o opt-in de WhatsApp. Reset (opt-out) preserva histórico para auditoria — ativações
   * anteriores ficam visíveis no {@code whatsapp_opt_in_at}.
   */
  @Transactional
  public void updateWhatsappOptIn(UUID userId, boolean optIn) {
    User user = userRepo.findById(userId).orElseThrow(() -> notFound(userId));
    user.setWhatsappOptIn(optIn);
    userRepo.save(user);
    log.info("privacy.optIn.updated userId={} optIn={}", userId, optIn);
  }

  /**
   * Anonimiza o titular (Art. 18, IV LGPD). Exige confirmação dupla (senha + texto). Após commit,
   * dispara {@link UserAnonymizedEvent} para o listener purgar o comprovante do MinIO fora da
   * transação.
   */
  @Transactional
  public void anonymizeSelf(UUID userId, String currentPassword) {
    User user = userRepo.findById(userId).orElseThrow(() -> notFound(userId));
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new PrivacyException("INVALID_PASSWORD", "Senha incorreta.");
    }
    String objectKeyToPurge = user.anonymize();
    userRepo.save(user);
    refreshTokenRepo.revokeAllByUserId(userId, "self_anonymize");
    events.publishEvent(new UserAnonymizedEvent(userId, objectKeyToPurge));
    log.info("privacy.anonymized userId={} hadProof={}", userId, objectKeyToPurge != null);
  }

  private static PrivacyException notFound(UUID userId) {
    log.warn("privacy: usuario {} nao encontrado", userId);
    return new PrivacyException("USER_NOT_FOUND", "Usuário não encontrado.");
  }
}
