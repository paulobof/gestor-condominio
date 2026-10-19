package br.com.condominio.feature.user;

import br.com.condominio.feature.password.PasswordHistory;
import br.com.condominio.feature.password.PasswordHistoryRepository;
import br.com.condominio.feature.user.dto.OwnProfileView;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Meu cadastro": o usuário logado consulta/edita os próprios dados (nome, saudação, telefone da
 * conta) e troca a própria senha. A troca exige a senha atual e aplica a mesma política anti-reuso
 * do reset (últimas 5 do {@link PasswordHistory}). A sessão atual é preservada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SelfProfileService {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final PasswordHistoryRepository historyRepo;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.security.password.pepper-version:1}")
  private short pepperVersion;

  @Transactional(readOnly = true)
  public OwnProfileView getProfile(UUID userId) {
    return toView(load(userId));
  }

  @Transactional
  public OwnProfileView updateProfile(
      UUID userId, String fullName, String greetingName, String phone) {
    User user = load(userId);
    user.updateOwnProfile(fullName, greetingName, phone);
    userRepo.save(user);
    log.info("self.profile.updated userId={}", userId);
    return toView(user);
  }

  @Transactional
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user = load(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      log.info("self.password.change rejected userId={} reason=invalid_current", userId);
      throw new SelfProfileException("INVALID_PASSWORD", "Senha atual incorreta.");
    }
    if (isReused(user, newPassword)) {
      throw new SelfProfileException("PASSWORD_REUSED", "Você já usou essa senha recentemente.");
    }

    String newHash = passwordEncoder.encode(newPassword);
    user.changePassword(newHash, pepperVersion);
    userRepo.save(user);
    historyRepo.save(PasswordHistory.create(user.getId(), newHash, pepperVersion));
    log.info("self.password.changed userId={}", userId);
  }

  /** Nova senha não pode ser a atual nem nenhuma das últimas 5 (mesma regra do reset). */
  private boolean isReused(User user, String newPassword) {
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      return true;
    }
    List<PasswordHistory> recent = historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(user.getId());
    return recent.stream().anyMatch(h -> passwordEncoder.matches(newPassword, h.getPasswordHash()));
  }

  private User load(UUID userId) {
    return userRepo
        .findById(userId)
        .orElseThrow(
            () -> {
              log.warn("self.profile: usuario {} nao encontrado", userId);
              return new SelfProfileException("USER_NOT_FOUND", "Usuário não encontrado.");
            });
  }

  private OwnProfileView toView(User user) {
    String email =
        emailRepo.findPrimaryByUserId(user.getId()).map(UserEmail::getEmail).orElse(null);
    return new OwnProfileView(user.getFullName(), user.getGreetingName(), user.getPhone(), email);
  }
}
