package br.com.condominio.feature.registration;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.PendingRegistrationView;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.registration.dto.RegistrationStatusResponse;
import br.com.condominio.feature.registration.event.UnitJoinRequestedEvent;
import br.com.condominio.feature.role.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final PasswordEncoder encoder;
  private final PermissionGrantService permissionGrants;
  private final org.springframework.context.ApplicationEventPublisher events;

  /**
   * Cadastro do morador, sem comprovante. Quem chega primeiro numa unidade sem master entra ACTIVE
   * e vira o master dela; os próximos entram PENDING_APPROVAL e são aprovados pelo master (ou, como
   * válvula de escape, pelo admin em {@code /api/registrations}).
   */
  @Transactional
  public RegistrationStatusResponse registerMaster(RegisterMasterRequest req, String clientIp) {

    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new RegistrationException("EMAIL_TAKEN", "Este e-mail já está cadastrado.");
    }

    Unit unit =
        unitRepo
            .findByCode(req.unitCode())
            .orElseThrow(
                () -> new RegistrationException("UNIT_NOT_FOUND", "Unidade não encontrada."));

    ConsentDocument consent =
        consentRepo
            .findByVersion(req.consentVersion())
            .orElseThrow(
                () ->
                    new RegistrationException(
                        "CONSENT_VERSION_INVALID", "Versão do termo de privacidade inválida."));

    Role residentRole =
        roleRepo
            .findByName(RoleName.RESIDENT)
            .orElseThrow(() -> new IllegalStateException("RESIDENT role missing"));

    boolean becomesMaster = unit.getMasterUserId() == null;

    User user = newInstance(User.class);
    setUserFields(user, req, unit, becomesMaster, consent, clientIp);
    user = userRepo.save(user);

    UserEmail userEmail = newInstance(UserEmail.class);
    setEmail(userEmail, user.getId(), req.email());
    emailRepo.save(userEmail);

    userRoleRepo.save(
        new UserRole(new UserRoleId(user.getId(), residentRole.getId()), Instant.now(), null));

    if (becomesMaster) {
      unit.assignMaster(user.getId());
      // grantedBy null = concedido pelo próprio cadastro, não por outra pessoa. O banco proíbe
      // auto-concessão (chk_grant_self em V3), então nunca passe o próprio usuário aqui.
      permissionGrants.grantIfAbsent(user.getId(), PermissionCode.RESIDENT_MANAGE, null);
      log.info(
          "Master auto-approved: userId={} unitCode={} ip={}",
          user.getId(),
          unit.getCode(),
          clientIp);
    } else {
      notifyMasterOfJoinRequest(unit, user);
      log.info(
          "Join request created: userId={} unitCode={} ip={}",
          user.getId(),
          unit.getCode(),
          clientIp);
    }

    return new RegistrationStatusResponse(user.getId(), user.getStatus().name());
  }

  /** Avisa o master da unidade que há um pedido de acesso esperando por ele. */
  private void notifyMasterOfJoinRequest(Unit unit, User requester) {
    userRepo
        .findById(unit.getMasterUserId())
        .filter(m -> m.getPhone() != null && !m.getPhone().isBlank())
        .ifPresent(
            master ->
                events.publishEvent(
                    new UnitJoinRequestedEvent(
                        requester.getId(),
                        master.getPhone(),
                        master.getGreetingName() != null
                            ? master.getGreetingName()
                            : master.getFullName(),
                        requester.getGreetingName() != null
                            ? requester.getGreetingName()
                            : requester.getFullName(),
                        unit.getCode())));
  }

  private void setUserFields(
      User user,
      RegisterMasterRequest req,
      Unit unit,
      boolean becomesMaster,
      ConsentDocument consent,
      String clientIp) {
    try {
      setField(user, "unitId", unit.getId());
      setField(user, "isUnitMaster", becomesMaster);
      setField(user, "fullName", req.fullName());
      setField(user, "greetingName", req.greetingName());
      setField(user, "phone", req.phone());
      setField(user, "passwordHash", encoder.encode(req.password()));
      setField(user, "passwordPepperVersion", (short) 1);
      setField(user, "mustChangePassword", false);
      setField(user, "status", becomesMaster ? UserStatus.ACTIVE : UserStatus.PENDING_APPROVAL);
      if (becomesMaster) {
        setField(user, "approvedAt", Instant.now());
      }
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

  @Transactional
  public Page<PendingRegistrationView> listPending(Pageable pageable) {
    return userRepo.findPendingResidents(pageable).map(this::toPendingView);
  }

  @Transactional
  public void approve(UUID userId, UUID approverId) {
    User user =
        userRepo
            .findById(userId)
            .orElseThrow(
                () -> new RegistrationException("USER_NOT_FOUND", "Usuário não encontrado"));
    if (user.isUnitMaster()) {
      user.approveAsMaster(approverId);
      Unit unit = unitRepo.findById(user.getUnitId()).orElseThrow();
      unit.assignMaster(user.getId());
      permissionGrants.grantIfAbsent(user.getId(), PermissionCode.RESIDENT_MANAGE, approverId);
      log.info("Master approved userId={} by approverId={}", userId, approverId);
      return;
    }
    // Morador comum: entra na unidade sem gestão e sem mastership (válvula do admin).
    user.approveAsMember(approverId);
    log.info("Member approved userId={} by approverId={}", userId, approverId);
  }

  @Transactional
  public void reject(UUID userId, UUID approverId, String reason) {
    User user =
        userRepo
            .findById(userId)
            .orElseThrow(
                () -> new RegistrationException("USER_NOT_FOUND", "Usuário não encontrado"));
    user.reject(approverId, reason);
    log.info("Master rejected userId={} by approverId={} reason='{}'", userId, approverId, reason);
  }

  private PendingRegistrationView toPendingView(User u) {
    String email =
        emailRepo.findByUserId(u.getId()).stream()
            .filter(UserEmail::isPrimary)
            .findFirst()
            .map(UserEmail::getEmail)
            .orElse(null);
    String unitCode =
        u.getUnitId() == null
            ? null
            : unitRepo.findById(u.getUnitId()).map(Unit::getCode).orElse(null);
    return new PendingRegistrationView(
        u.getId(),
        u.getFullName(),
        email,
        u.getPhone(),
        unitCode,
        u.getGender() == null ? null : u.getGender().name(),
        u.getBirthDate(),
        u.getCreatedAt());
  }
}
