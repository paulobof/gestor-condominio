package br.com.condominio.feature.user;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.feature.activity.ActivityAction;
import br.com.condominio.feature.activity.ActivityNotifier;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.MyUnitView;
import br.com.condominio.feature.user.dto.UnitJoinRequestResponse;
import br.com.condominio.feature.user.dto.UnitMemberDetail;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import br.com.condominio.feature.user.event.MemberEmailChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de moradores pelo morador master da unidade. Autorização ({@code RESIDENT_MANAGE}) é feita
 * no controller; o escopo (alvo na unidade do master, não-master) é garantido aqui. Reusa a
 * mecânica comum de provisionamento ({@link UserProvisioning}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitMemberService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleRepository roleRepo;
  private final UserProvisioning provisioning;
  private final ApplicationEventPublisher eventPublisher;
  private final UnitRepository unitRepo;
  private final ActivityNotifier activityNotifier;

  @Transactional(readOnly = true)
  public List<UnitMemberResponse> listMyUnitMembers(UUID masterUserId) {
    User master = userRepo.findById(masterUserId).orElse(null);
    if (master == null) {
      return List.of();
    }
    List<UUID> myUnits = myUnitIds(master);
    if (myUnits.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> codeByUnit = unitCodes(myUnits);
    return userRepo
        .findByUnitIdInAndStatusNotAndIsUnitMasterFalse(myUnits, UserStatus.ANONYMIZED)
        .stream()
        .filter(u -> u.getStatus() != UserStatus.PENDING_APPROVAL)
        .map(u -> toResponse(u, codeByUnit.get(u.getUnitId())))
        .toList();
  }

  /**
   * Unidades sob gestão do usuário (posses APPROVED, ou a unidade única no fallback single-unit).
   */
  @Transactional(readOnly = true)
  public List<MyUnitView> listMyUnits(UUID masterUserId) {
    User master = userRepo.findById(masterUserId).orElse(null);
    if (master == null) {
      return List.of();
    }
    List<UUID> myUnits = myUnitIds(master);
    Map<UUID, String> codes = unitCodes(myUnits);
    return myUnits.stream().map(id -> new MyUnitView(id, codes.get(id))).toList();
  }

  @Transactional(readOnly = true)
  public UnitMemberDetail getMemberDetail(UUID masterUserId, UUID memberId) {
    List<UUID> myUnits = myUnitIds(requireMaster(masterUserId));
    User member = requireMemberInMyUnits(memberId, myUnits);
    String email = emailRepo.findPrimaryByUserId(memberId).map(UserEmail::getEmail).orElse(null);
    return new UnitMemberDetail(
        member.getId(),
        member.getFullName(),
        member.getGreetingName(),
        member.getPhone(),
        email,
        member.getGender() == null ? null : member.getGender().name(),
        member.getBirthDate());
  }

  @Transactional
  public CreatedUnitMemberResponse createMember(UUID masterUserId, CreateUnitMemberRequest req) {
    User master = requireMaster(masterUserId);
    UUID unitId = resolveTargetUnit(req.unitId(), myUnitIds(master), master);
    Role residentRole = roleRepo.findByName(RoleName.RESIDENT).orElseThrow();

    UserProvisioning.Provisioned provisioned =
        provisioning.createActiveUser(unitId, req.fullName(), req.phone(), req.email());
    User member = provisioned.user();
    member.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        unitId,
        req.gender(),
        req.birthDate());
    member.setWhatsappOptIn(req.whatsappOptIn());

    userRoleRepo.save(
        new UserRole(
            new UserRoleId(member.getId(), residentRole.getId()), Instant.now(), masterUserId));

    activityNotifier.notify(ActivityAction.CREATED, "Morador", unitCode(unitId), masterUserId);
    log.info("Master {} criou morador {}", masterUserId, member.getId());
    return new CreatedUnitMemberResponse(
        member.getId(), member.getFullName(), provisioned.provisionalPassword());
  }

  @Transactional
  public void updateMember(UUID masterUserId, UUID memberId, UpdateUnitMemberRequest req) {
    User member = requireMemberInMyUnits(memberId, myUnitIds(requireMaster(masterUserId)));
    UUID unitId = member.getUnitId();

    // Detecta mudança de e-mail ANTES de aplicar (comparação case-insensitive).
    // changePrimaryEmail é no-op se igual — então detectamos aqui para não notificar em vão.
    String currentEmail =
        emailRepo.findPrimaryByUserId(memberId).map(UserEmail::getEmail).orElse(null);
    boolean emailChanged =
        currentEmail == null || !currentEmail.equalsIgnoreCase(req.email().trim());

    provisioning.changePrimaryEmail(memberId, req.email());
    member.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        unitId,
        req.gender(),
        req.birthDate());
    activityNotifier.notify(ActivityAction.UPDATED, "Morador", unitCode(unitId), masterUserId);
    log.info("Master {} atualizou morador {}", masterUserId, memberId);

    if (emailChanged) {
      eventPublisher.publishEvent(
          new MemberEmailChangedEvent(memberId, member.getPhone(), member.getGreetingName()));
    }
  }

  @Transactional
  public void deleteMember(UUID masterUserId, UUID memberId) {
    User member = requireMemberInMyUnits(memberId, myUnitIds(requireMaster(masterUserId)));
    activityNotifier.notify(
        ActivityAction.DELETED, "Morador", unitCode(member.getUnitId()), masterUserId);
    provisioning.softDelete(member, memberId);
    log.info("Master {} excluiu (soft) morador {}", masterUserId, memberId);
  }

  // ===== pedidos de acesso à unidade =====

  /** Pedidos parados nas minhas unidades — quem se cadastrou informando uma unidade já minha. */
  @Transactional(readOnly = true)
  public List<UnitJoinRequestResponse> listPendingRequests(UUID masterUserId) {
    User master = userRepo.findById(masterUserId).orElse(null);
    if (master == null) {
      return List.of();
    }
    List<UUID> myUnits = myUnitIds(master);
    if (myUnits.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> codeByUnit = unitCodes(myUnits);
    return userRepo
        .findByUnitIdInAndStatusAndIsUnitMasterFalse(myUnits, UserStatus.PENDING_APPROVAL)
        .stream()
        .map(
            u ->
                new UnitJoinRequestResponse(
                    u.getId(),
                    u.getFullName(),
                    u.getGreetingName(),
                    emailRepo.findPrimaryByUserId(u.getId()).map(UserEmail::getEmail).orElse(null),
                    u.getPhone(),
                    u.getUnitId(),
                    codeByUnit.get(u.getUnitId()),
                    u.getCreatedAt()))
        .toList();
  }

  /** Aprova o pedido: o morador entra na unidade, sem mastership e sem gestão. */
  @Transactional
  public void approveRequest(UUID masterUserId, UUID requestUserId) {
    User master = requireMaster(masterUserId);
    User pending = requirePendingRequestInMyUnits(requestUserId, myUnitIds(master));
    pending.approveAsMember(masterUserId);
    activityNotifier.notify(
        ActivityAction.UPDATED, "Pedido de acesso", unitCode(pending.getUnitId()), masterUserId);
    log.info("Master {} aprovou pedido de acesso {}", masterUserId, requestUserId);
  }

  /** Recusa o pedido. O usuário fica REJECTED e não entra. */
  @Transactional
  public void rejectRequest(UUID masterUserId, UUID requestUserId, String reason) {
    User master = requireMaster(masterUserId);
    User pending = requirePendingRequestInMyUnits(requestUserId, myUnitIds(master));
    pending.reject(masterUserId, reason);
    activityNotifier.notify(
        ActivityAction.DELETED, "Pedido de acesso", unitCode(pending.getUnitId()), masterUserId);
    log.info("Master {} recusou pedido de acesso {}", masterUserId, requestUserId);
  }

  /** Garante que o pedido existe, está pendente, é de uma unidade minha e não é de um master. */
  private User requirePendingRequestInMyUnits(UUID requestUserId, List<UUID> myUnits) {
    User pending =
        userRepo
            .findById(requestUserId)
            .orElseThrow(
                () -> new UnitMemberException("REQUEST_NOT_FOUND", "Pedido não encontrado."));
    if (myUnits.isEmpty() || !myUnits.contains(pending.getUnitId()) || pending.isUnitMaster()) {
      throw new UnitMemberException("REQUEST_NOT_IN_UNIT", "Este pedido não é da sua unidade.");
    }
    if (pending.getStatus() != UserStatus.PENDING_APPROVAL) {
      throw new UnitMemberException("REQUEST_NOT_PENDING", "Este pedido já foi resolvido.");
    }
    return pending;
  }

  // ===== helpers de escopo =====

  private User requireMaster(UUID masterUserId) {
    User master =
        userRepo
            .findById(masterUserId)
            .orElseThrow(() -> new AccessException("USER_NOT_FOUND", "Usuário não encontrado."));
    if (master.getStatus() != UserStatus.ACTIVE) {
      throw new AccessException("USER_NOT_ACTIVE", "Usuário master não está ativo.");
    }
    if (!master.isUnitMaster()) {
      throw new UnitMemberException("NOT_A_MASTER", "Apenas o morador master gere a unidade.");
    }
    if (master.getUnitId() == null) {
      throw new UnitMemberException("MASTER_HAS_NO_UNIT", "Master sem unidade associada.");
    }
    return master;
  }

  /** Garante que o alvo existe, está em uma das minhas unidades, é não-master e está ACTIVE. */
  private User requireMemberInMyUnits(UUID memberId, List<UUID> myUnits) {
    User member =
        userRepo
            .findById(memberId)
            .orElseThrow(
                () -> new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não encontrado."));
    if (myUnits.isEmpty() || !myUnits.contains(member.getUnitId()) || member.isUnitMaster()) {
      throw new UnitMemberException(
          "MEMBER_NOT_IN_UNIT", "Este morador não pertence à sua unidade.");
    }
    if (member.getStatus() != UserStatus.ACTIVE) {
      throw new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não está ativo.");
    }
    return member;
  }

  /** Unidades sob gestão do master: a unidade única do {@code User.unitId}. */
  private List<UUID> myUnitIds(User master) {
    return master.getUnitId() == null ? List.of() : List.of(master.getUnitId());
  }

  /**
   * Resolve em qual unidade cadastrar: a pedida (deve ser minha) ou, sem pedido, a unidade única.
   */
  private UUID resolveTargetUnit(UUID requested, List<UUID> myUnits, User master) {
    if (requested != null) {
      if (!myUnits.contains(requested)) {
        throw new UnitMemberException("UNIT_NOT_MINE", "Esta unidade não é sua.");
      }
      return requested;
    }
    if (master.getUnitId() != null) {
      return master.getUnitId();
    }
    if (myUnits.size() == 1) {
      return myUnits.get(0);
    }
    throw new UnitMemberException("UNIT_REQUIRED", "Selecione a unidade do morador.");
  }

  private Map<UUID, String> unitCodes(List<UUID> unitIds) {
    return unitRepo.findAllById(unitIds).stream()
        .collect(Collectors.toMap(Unit::getId, Unit::getCode));
  }

  private String unitCode(UUID unitId) {
    return unitId == null ? null : unitRepo.findById(unitId).map(Unit::getCode).orElse(null);
  }

  private UnitMemberResponse toResponse(User u, String unitCode) {
    String email = emailRepo.findPrimaryByUserId(u.getId()).map(UserEmail::getEmail).orElse(null);
    return new UnitMemberResponse(
        u.getId(),
        u.getFullName(),
        u.getGreetingName(),
        email,
        u.getPhone(),
        u.getStatus().name(),
        u.getUnitId(),
        unitCode);
  }

  private static String trimToNull(String s) {
    return (s == null || s.isBlank()) ? null : s.trim();
  }
}
