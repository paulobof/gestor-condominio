package br.com.condominio.feature.user;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitOwnershipRepository;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.MyUnitView;
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
import org.springframework.beans.factory.annotation.Value;
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
  private final UnitOwnershipRepository ownershipRepo;
  private final UnitRepository unitRepo;

  @Value("${app.feature.unitownership.enabled:false}")
  private boolean unitOwnershipEnabled;

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
    log.info("Master {} atualizou morador {}", masterUserId, memberId);

    if (emailChanged) {
      eventPublisher.publishEvent(
          new MemberEmailChangedEvent(memberId, member.getPhone(), member.getGreetingName()));
    }
  }

  @Transactional
  public void deleteMember(UUID masterUserId, UUID memberId) {
    User member = requireMemberInMyUnits(memberId, myUnitIds(requireMaster(masterUserId)));
    provisioning.softDelete(member, memberId);
    log.info("Master {} excluiu (soft) morador {}", masterUserId, memberId);
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

  /**
   * Unidades sob gestão do master. Com a flag on e posses APPROVED, usa-as (multi-unidade); caso
   * contrário, faz fallback para a unidade única do {@code User.unitId} (comportamento atual).
   */
  private List<UUID> myUnitIds(User master) {
    if (unitOwnershipEnabled) {
      List<UUID> approved = ownershipRepo.findApprovedUnitIdsByUser(master.getId());
      if (!approved.isEmpty()) {
        return approved;
      }
    }
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
