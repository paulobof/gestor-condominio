package br.com.condominio.feature.role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionGrantServiceTest {

  @Mock private PermissionRepository permissionRepo;
  @Mock private UserPermissionGrantRepository grantRepo;
  @InjectMocks private PermissionGrantService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID grantedBy = UUID.randomUUID();

  @Test
  void grantsWhenAbsent() {
    Permission perm = mock(Permission.class);
    when(perm.getId()).thenReturn((short) 17);
    when(permissionRepo.findByCode(PermissionCode.RESIDENT_MANAGE)).thenReturn(Optional.of(perm));
    when(grantRepo.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of());

    service.grantIfAbsent(userId, PermissionCode.RESIDENT_MANAGE, grantedBy);

    verify(grantRepo)
        .save(
            argThat(
                g ->
                    g.getUserId().equals(userId)
                        && g.getPermissionId().equals((short) 17)
                        && grantedBy.equals(g.getGrantedByUserId())));
  }

  @Test
  void skipsWhenAlreadyGranted() {
    Permission perm = mock(Permission.class);
    when(perm.getId()).thenReturn((short) 17);
    when(permissionRepo.findByCode(PermissionCode.RESIDENT_MANAGE)).thenReturn(Optional.of(perm));
    UserPermissionGrant existing = mock(UserPermissionGrant.class);
    when(existing.getPermissionId()).thenReturn((short) 17);
    when(grantRepo.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(existing));

    service.grantIfAbsent(userId, PermissionCode.RESIDENT_MANAGE, grantedBy);

    verify(grantRepo, never()).save(any());
  }
}
