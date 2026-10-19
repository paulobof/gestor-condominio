package br.com.condominio.feature.user;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Mecânica de provisionamento de usuário compartilhada por {@code AccessService} (admin) e {@code
 * UnitMemberService} (master): criar {@link User} ACTIVE + {@link UserEmail} primário único (senha
 * aleatória que ninguém conhece — a pessoa cria a sua pelo convite no WhatsApp), trocar o e-mail
 * primário com unicidade (flush → EMAIL_TAKEN) e soft delete que libera o e-mail. Sem
 * {@code @Transactional}: roda dentro da transação do service chamador. Erros usam {@link
 * AccessException} ({@code EMAIL_TAKEN}), já mapeada para 409 no handler global.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioning {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final PasswordEncoder encoder;
  private final ProvisionalPasswordGenerator passwordGenerator;

  /**
   * Cria um usuário ACTIVE (não-master) na unidade informada, com e-mail primário único e uma senha
   * forte aleatória (só o hash é guardado; nunca é devolvida nem exibida) e
   * must_change_password=true. A pessoa define a própria senha pelo convite ({@code
   * PasswordResetService.issueInvite}). Lança {@code EMAIL_TAKEN} se o e-mail já existe.
   */
  public User createActiveUser(UUID unitId, String fullName, String phone, String email) {
    String trimmedEmail = email.trim();
    if (emailRepo.findActiveByEmailIgnoreCase(trimmedEmail).isPresent()) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
    User user =
        User.newActiveByAdmin(
            unitId,
            fullName.trim(),
            phone.trim(),
            encoder.encode(passwordGenerator.generate()),
            (short) 1);
    user = userRepo.save(user);
    emailRepo.save(UserEmail.primary(user.getId(), trimmedEmail));
    return user;
  }

  /**
   * Troca o e-mail primário do usuário, forçando a unicidade (ux_user_email_email_active) no flush.
   * No-op se o novo e-mail é igual (case-insensitive) ao atual. Cria o primário se ausente.
   */
  public void changePrimaryEmail(UUID userId, String newEmail) {
    String trimmed = newEmail.trim();
    Optional<UserEmail> primary = emailRepo.findPrimaryByUserId(userId);
    String currentEmail = primary.map(UserEmail::getEmail).orElse(null);
    if (currentEmail != null && currentEmail.equalsIgnoreCase(trimmed)) {
      return; // mesmo e-mail: nada a fazer
    }
    emailRepo
        .findActiveByEmailIgnoreCase(trimmed)
        .ifPresent(
            e -> {
              if (!e.getUserId().equals(userId)) {
                throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
              }
            });
    primary.ifPresentOrElse(
        p -> p.changeEmail(trimmed), () -> emailRepo.save(UserEmail.primary(userId, trimmed)));
    try {
      emailRepo.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new AccessException("EMAIL_TAKEN", "E-mail já cadastrado.");
    }
  }

  /**
   * Soft delete do usuário: libera o e-mail (soft delete dos UserEmail) e depois o próprio User.
   */
  public void softDelete(User user, UUID userId) {
    emailRepo.findByUserId(userId).forEach(emailRepo::delete);
    userRepo.delete(user);
  }
}
