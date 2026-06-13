package br.com.condominio.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.access.AccessException;
import br.com.condominio.shared.security.ProvisionalPasswordGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserProvisioningTest {

  private static final UUID TARGET = UUID.randomUUID();

  @Mock private UserRepository userRepo;
  @Mock private UserEmailRepository emailRepo;
  @Mock private PasswordEncoder encoder;
  @Mock private ProvisionalPasswordGenerator passwordGenerator;

  @InjectMocks private UserProvisioning provisioning;

  @Test
  void createActiveUser_savesUserAndPrimaryEmail_returnsProvisionalPassword() {
    when(emailRepo.findActiveByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
    when(passwordGenerator.generate()).thenReturn("Abc123!xYZ09__a");
    when(encoder.encode("Abc123!xYZ09__a")).thenReturn("HASH");
    User saved = mock(User.class);
    when(saved.getId()).thenReturn(TARGET);
    when(userRepo.save(any(User.class))).thenReturn(saved);

    UserProvisioning.Provisioned out =
        provisioning.createActiveUser(UUID.randomUUID(), "Ana Lima", "+5511999999999", "ana@x.com");

    assertThat(out.user()).isSameAs(saved);
    assertThat(out.provisionalPassword()).isEqualTo("Abc123!xYZ09__a");
    verify(emailRepo).save(any(UserEmail.class));
  }

  @Test
  void createActiveUser_emailTaken_throwsConflict() {
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com"))
        .thenReturn(Optional.of(mock(UserEmail.class)));

    assertThatThrownBy(
            () ->
                provisioning.createActiveUser(
                    UUID.randomUUID(), "Ana", "+5511999999999", "dup@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
    verify(userRepo, never()).save(any());
  }

  @Test
  void changePrimaryEmail_differentEmail_changesAndFlushes() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

    provisioning.changePrimaryEmail(TARGET, "new@x.com");

    verify(primary).changeEmail("new@x.com");
    verify(emailRepo).flush();
  }

  @Test
  void changePrimaryEmail_sameEmailIgnoreCase_isNoOp() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("ana@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));

    provisioning.changePrimaryEmail(TARGET, "ANA@x.com");

    verify(primary, never()).changeEmail(any());
  }

  @Test
  void changePrimaryEmail_takenByOther_throwsConflict() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    UserEmail other = mock(UserEmail.class);
    when(other.getUserId()).thenReturn(UUID.randomUUID());
    when(emailRepo.findActiveByEmailIgnoreCase("dup@x.com")).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> provisioning.changePrimaryEmail(TARGET, "dup@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void changePrimaryEmail_collisionOnFlush_throwsConflict() {
    UserEmail primary = mock(UserEmail.class);
    when(primary.getEmail()).thenReturn("old@x.com");
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.of(primary));
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
    doThrow(new DataIntegrityViolationException("dup")).when(emailRepo).flush();

    assertThatThrownBy(() -> provisioning.changePrimaryEmail(TARGET, "new@x.com"))
        .isInstanceOf(AccessException.class)
        .extracting("code")
        .isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void changePrimaryEmail_noPrimary_createsPrimary() {
    when(emailRepo.findPrimaryByUserId(TARGET)).thenReturn(Optional.empty());
    when(emailRepo.findActiveByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

    provisioning.changePrimaryEmail(TARGET, "new@x.com");

    verify(emailRepo).save(any(UserEmail.class));
    verify(emailRepo).flush();
  }

  @Test
  void softDelete_deletesEmailsAndUser() {
    User target = mock(User.class);
    UserEmail e = mock(UserEmail.class);
    when(emailRepo.findByUserId(TARGET)).thenReturn(List.of(e));

    provisioning.softDelete(target, TARGET);

    verify(emailRepo).delete(e);
    verify(userRepo).delete(target);
  }
}
