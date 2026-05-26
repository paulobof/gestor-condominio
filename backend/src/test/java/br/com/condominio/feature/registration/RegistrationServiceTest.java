package br.com.condominio.feature.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.condominio.feature.consent.ConsentDocument;
import br.com.condominio.feature.registration.dto.RegisterMasterRequest;
import br.com.condominio.feature.role.*;
import br.com.condominio.feature.unit.Unit;
import br.com.condominio.feature.unit.UnitRepository;
import br.com.condominio.feature.user.*;
import br.com.condominio.storage.FileStorage;
import br.com.condominio.storage.MagicBytesValidator;
import br.com.condominio.storage.MinioProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {

  private UnitRepository unitRepo;
  private UserRepository userRepo;
  private UserEmailRepository emailRepo;
  private RoleRepository roleRepo;
  private UserRoleRepository userRoleRepo;
  private ConsentDocumentRepository consentRepo;
  private FileStorage storage;
  private MagicBytesValidator magicBytes;
  private PasswordEncoder encoder;
  private MinioProperties props;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    unitRepo = mock(UnitRepository.class);
    userRepo = mock(UserRepository.class);
    emailRepo = mock(UserEmailRepository.class);
    roleRepo = mock(RoleRepository.class);
    userRoleRepo = mock(UserRoleRepository.class);
    consentRepo = mock(ConsentDocumentRepository.class);
    storage = mock(FileStorage.class);
    magicBytes = mock(MagicBytesValidator.class);
    encoder = mock(PasswordEncoder.class);
    props = new MinioProperties();
    props.setBucketProofs("residence-proofs");
    service =
        new RegistrationService(
            unitRepo,
            userRepo,
            emailRepo,
            roleRepo,
            userRoleRepo,
            consentRepo,
            storage,
            magicBytes,
            encoder,
            props);
  }

  @Test
  void registersMasterSuccessfully() {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com")).thenReturn(Optional.empty());
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForProof("application/pdf")).thenReturn(true);
    Unit unit = newInstance(Unit.class);
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "code", "702C");
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    when(consentRepo.findByVersion("1.0.0")).thenReturn(Optional.of(newConsent("1.0.0")));
    when(encoder.encode(any())).thenReturn("hashed");
    Role role = newInstance(Role.class);
    setField(role, "id", (short) 4);
    when(roleRepo.findByName(RoleName.RESIDENT)).thenReturn(Optional.of(role));
    when(storage.upload(eq("residence-proofs"), any(), anyLong(), eq("application/pdf")))
        .thenReturn("object-key-uuid");
    when(userRepo.save(any()))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              setField(u, "id", UUID.randomUUID());
              return u;
            });

    var req =
        new RegisterMasterRequest(
            "Paulo Teste",
            "Paulo",
            "paulo@x.com",
            "+5511999999999",
            "MALE",
            LocalDate.of(1990, 1, 1),
            "702C",
            "Senha@1234",
            "1.0.0",
            true);
    byte[] pdf = {0x25, 0x50, 0x44, 0x46};
    MockMultipartFile file =
        new MockMultipartFile("proof", "comprovante.pdf", "application/pdf", pdf);

    var resp = service.registerMaster(req, file, "127.0.0.1");

    assertThat(resp.status()).isEqualTo("PENDING_APPROVAL");
    verify(storage)
        .upload(eq("residence-proofs"), any(), eq((long) pdf.length), eq("application/pdf"));
    verify(emailRepo).save(any());
    verify(userRoleRepo).save(any());
  }

  @Test
  void rejectsWhenEmailAlreadyExists() {
    when(emailRepo.findActiveByEmailIgnoreCase("paulo@x.com"))
        .thenReturn(Optional.of(newInstance(UserEmail.class)));
    var req = baseReq();
    var file =
        new MockMultipartFile(
            "proof", "f.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("e-mail");
  }

  @Test
  void rejectsWhenUnitAlreadyHasMaster() {
    when(emailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    Unit unit = newInstance(Unit.class);
    setField(unit, "id", UUID.randomUUID());
    setField(unit, "masterUserId", UUID.randomUUID());
    when(unitRepo.findByCode("702C")).thenReturn(Optional.of(unit));
    var req = baseReq();
    var file =
        new MockMultipartFile(
            "proof", "f.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
    when(magicBytes.detect(any())).thenReturn("application/pdf");
    when(magicBytes.isAcceptedForProof(any())).thenReturn(true);
    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("master");
  }

  @Test
  void rejectsWhenFileTypeNotAccepted() {
    when(emailRepo.findActiveByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    when(magicBytes.detect(any())).thenReturn("application/zip");
    when(magicBytes.isAcceptedForProof("application/zip")).thenReturn(false);
    var req = baseReq();
    var file = new MockMultipartFile("proof", "f.zip", "application/zip", new byte[] {0x50, 0x4B});
    assertThatThrownBy(() -> service.registerMaster(req, file, "127.0.0.1"))
        .isInstanceOf(RegistrationException.class)
        .hasMessageContaining("comprovante");
  }

  private RegisterMasterRequest baseReq() {
    return new RegisterMasterRequest(
        "Paulo",
        "Paulo",
        "paulo@x.com",
        "+5511999999999",
        "MALE",
        LocalDate.of(1990, 1, 1),
        "702C",
        "Senha@1234",
        "1.0.0",
        false);
  }

  private ConsentDocument newConsent(String v) {
    ConsentDocument c = newInstance(ConsentDocument.class);
    setField(c, "version", v);
    setField(c, "publishedAt", Instant.now());
    return c;
  }

  static <T> T newInstance(Class<T> clazz) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot instantiate " + clazz.getSimpleName(), e);
    }
  }

  static void setField(Object target, String name, Object value) {
    try {
      var f = findField(target.getClass(), name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static java.lang.reflect.Field findField(Class<?> c, String name) throws NoSuchFieldException {
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ex) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
