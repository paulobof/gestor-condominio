package br.com.condominio.feature.privacy;

import br.com.condominio.feature.auth.RefreshTokenRepository;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserEmail;
import br.com.condominio.feature.user.UserEmailRepository;
import br.com.condominio.feature.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonimização da conta (LGPD Art. 18, IV) — a mesma para o pedido do titular e para a conta sem
 * uso. Tira a PII do usuário e dos e-mails, encerra as sessões e libera a unidade se ele era o
 * master (senão a unidade ficaria sem ninguém para aprovar novos moradores).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAnonymizer {

  private final UserRepository userRepo;
  private final UserEmailRepository emailRepo;
  private final UnitRepository unitRepo;
  private final RefreshTokenRepository refreshTokenRepo;

  @Transactional
  public void anonymize(User user, String reason) {
    if (user.getUnitId() != null && user.isUnitMaster()) {
      unitRepo.findById(user.getUnitId()).ifPresent(u -> u.releaseMaster(user.getId()));
    }
    user.anonymize();
    userRepo.save(user);
    emailRepo.findByUserId(user.getId()).forEach(UserEmail::anonymize);
    refreshTokenRepo.revokeAllByUserId(user.getId(), reason);
    log.info("privacy.anonymized userId={} reason={}", user.getId(), reason);
  }
}
