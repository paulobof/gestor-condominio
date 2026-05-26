package br.com.condominio.feature.user;

import br.com.condominio.feature.role.*;
import br.com.condominio.feature.user.dto.*;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitMemberService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UserRoleRepository userRoleRepo;
  private final RoleRepository roleRepo;
  private final PasswordEncoder encoder;

  @Transactional
  public List<UnitMemberResponse> listMyUnitMembers(UUID masterUserId) {
    User master =
        userRepo
            .findById(masterUserId)
            .orElseThrow(() -> new IllegalStateException("master missing"));
    UUID unitId = master.getUnitId();
    if (unitId == null) return List.of();
    return userRepo.findAll().stream()
        .filter(u -> unitId.equals(u.getUnitId()) && !u.getId().equals(masterUserId))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public UnitMemberResponse createMember(UUID masterUserId, CreateUnitMemberRequest req) {
    User master = userRepo.findById(masterUserId).orElseThrow();
    if (!master.isUnitMaster()) {
      throw new IllegalStateException("Only masters can create members.");
    }
    if (emailRepo.findActiveByEmailIgnoreCase(req.email()).isPresent()) {
      throw new IllegalStateException("E-mail já cadastrado.");
    }

    Role residentRole = roleRepo.findByName(RoleName.RESIDENT).orElseThrow();

    User member = new User();
    try {
      setField(member, "unitId", master.getUnitId());
      setField(member, "isUnitMaster", false);
      setField(member, "fullName", req.fullName());
      setField(member, "greetingName", req.greetingName());
      setField(member, "phone", req.phone());
      if (req.gender() != null && !req.gender().isBlank()) {
        setField(member, "gender", Gender.valueOf(req.gender()));
      }
      setField(member, "birthDate", req.birthDate());
      setField(member, "passwordHash", encoder.encode(req.password()));
      setField(member, "passwordPepperVersion", (short) 1);
      setField(member, "mustChangePassword", true);
      setField(member, "status", UserStatus.ACTIVE);
      setField(member, "whatsappOptIn", req.whatsappOptIn());
      if (req.whatsappOptIn()) setField(member, "whatsappOptInAt", Instant.now());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    member = userRepo.save(member);

    UserEmail e = new UserEmail();
    try {
      setField(e, "userId", member.getId());
      setField(e, "email", req.email());
      setField(e, "isPrimary", true);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    emailRepo.save(e);

    UserRole ur =
        new UserRole(
            new UserRoleId(member.getId(), residentRole.getId()), Instant.now(), masterUserId);
    userRoleRepo.save(ur);

    log.info("Master {} created member {}", masterUserId, member.getId());
    return toResponse(member);
  }

  @Transactional
  public void disableMember(UUID masterUserId, UUID memberId) {
    User master = userRepo.findById(masterUserId).orElseThrow();
    User member = userRepo.findById(memberId).orElseThrow();
    if (!member.getUnitId().equals(master.getUnitId())) {
      throw new IllegalStateException("Member not in your unit.");
    }
    if (member.isUnitMaster()) {
      throw new IllegalStateException("Cannot disable master via this endpoint.");
    }
    member.disable();
    log.info("Master {} disabled member {}", masterUserId, memberId);
  }

  private UnitMemberResponse toResponse(User u) {
    String email =
        emailRepo.findByUserId(u.getId()).stream()
            .filter(UserEmail::isPrimary)
            .findFirst()
            .map(UserEmail::getEmail)
            .orElse(null);
    return new UnitMemberResponse(
        u.getId(), u.getFullName(), u.getGreetingName(), email, u.getPhone(), u.getStatus().name());
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
