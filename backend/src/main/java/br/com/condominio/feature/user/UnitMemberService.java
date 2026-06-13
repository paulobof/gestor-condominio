package br.com.condominio.feature.user;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.feature.role.Role;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.role.RoleRepository;
import br.com.condominio.feature.role.UserRole;
import br.com.condominio.feature.role.UserRoleId;
import br.com.condominio.feature.role.UserRoleRepository;
import br.com.condominio.feature.user.dto.CreateUnitMemberRequest;
import br.com.condominio.feature.user.dto.CreatedUnitMemberResponse;
import br.com.condominio.feature.user.dto.UnitMemberResponse;
import br.com.condominio.feature.user.dto.UpdateUnitMemberRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @Transactional(readOnly = true)
  public List<UnitMemberResponse> listMyUnitMembers(UUID masterUserId) {
    UUID unitId = userRepo.findById(masterUserId).map(User::getUnitId).orElse(null);
    if (unitId == null) {
      return List.of();
    }
    return userRepo
        .findByUnitIdAndStatusNotAndIsUnitMasterFalse(unitId, UserStatus.ANONYMIZED)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public CreatedUnitMemberResponse createMember(UUID masterUserId, CreateUnitMemberRequest req) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
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
    UUID unitId = requireMaster(masterUserId).getUnitId();
    User member = requireMemberInUnit(memberId, unitId);

    provisioning.changePrimaryEmail(memberId, req.email());
    member.updateProfile(
        req.fullName().trim(),
        trimToNull(req.greetingName()),
        req.phone().trim(),
        unitId,
        req.gender(),
        req.birthDate());
    log.info("Master {} atualizou morador {}", masterUserId, memberId);
  }

  @Transactional
  public void deleteMember(UUID masterUserId, UUID memberId) {
    UUID unitId = requireMaster(masterUserId).getUnitId();
    User member = requireMemberInUnit(memberId, unitId);
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

  /** Garante que o alvo existe, está na unidade do master, é não-master e está ACTIVE. */
  private User requireMemberInUnit(UUID memberId, UUID masterUnitId) {
    User member =
        userRepo
            .findById(memberId)
            .orElseThrow(
                () -> new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não encontrado."));
    if (masterUnitId == null || !masterUnitId.equals(member.getUnitId()) || member.isUnitMaster()) {
      throw new UnitMemberException(
          "MEMBER_NOT_IN_UNIT", "Este morador não pertence à sua unidade.");
    }
    if (member.getStatus() != UserStatus.ACTIVE) {
      throw new UnitMemberException("MEMBER_NOT_IN_UNIT", "Morador não está ativo.");
    }
    return member;
  }

  private UnitMemberResponse toResponse(User u) {
    String email = emailRepo.findPrimaryByUserId(u.getId()).map(UserEmail::getEmail).orElse(null);
    return new UnitMemberResponse(
        u.getId(), u.getFullName(), u.getGreetingName(), email, u.getPhone(), u.getStatus().name());
  }

  private static String trimToNull(String s) {
    return (s == null || s.isBlank()) ? null : s.trim();
  }
}
