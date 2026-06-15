package br.com.condominio.feature.parkingrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.parkingrental.dto.CreateParkingRentalRequest;
import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.feature.parkingrental.dto.UpdateParkingRentalRequest;
import br.com.condominio.feature.user.User;
import br.com.condominio.feature.user.UserRepository;
import br.com.condominio.feature.whatsapp.PhoneNumberNormalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingRentalServiceTest {

  @Mock private ParkingRentalRepository repo;
  @Mock private UserRepository userRepo;
  @Mock private PhoneNumberNormalizer normalizer;
  @InjectMocks private ParkingRentalService service;

  private final UUID author = UUID.randomUUID();
  private final UUID rentalId = UUID.randomUUID();

  private ParkingRental sample() {
    return ParkingRental.create(author, "A", "-1", "045", new BigDecimal("350.00"));
  }

  @BeforeEach
  void stubAuthorLookup() {
    User u = org.mockito.Mockito.mock(User.class);
    lenient().when(u.getId()).thenReturn(author);
    lenient().when(u.getFullName()).thenReturn("Ana Costa");
    lenient().when(u.getPhone()).thenReturn("11999990000");
    lenient().when(userRepo.findAllById(any())).thenReturn(List.of(u));
    lenient().when(normalizer.toEvolutionNumber("11999990000")).thenReturn("5511999990000");
  }

  @Test
  void create_savesActive_andResolvesContact() {
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.create(
            author, new CreateParkingRentalRequest("A", "-1", "045", new BigDecimal("350.00")));
    assertThat(v.status()).isEqualTo(ParkingRentalStatus.ACTIVE);
    assertThat(v.authorName()).isEqualTo("Ana Costa");
    assertThat(v.authorPhone()).isEqualTo("11999990000");
    assertThat(v.authorWhatsapp()).isEqualTo("5511999990000");
    verify(repo).save(any(ParkingRental.class));
  }

  @Test
  void update_byOwner_succeeds() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.update(
            rentalId,
            author,
            false,
            new UpdateParkingRentalRequest("B", "2", "B-200", new BigDecimal("500.00"), null));
    assertThat(v.tower()).isEqualTo("B");
    assertThat(v.spotNumber()).isEqualTo("B-200");
  }

  @Test
  void update_byNonOwnerWithoutModerate_throwsForbidden() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    UUID stranger = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                service.update(
                    rentalId,
                    stranger,
                    false,
                    new UpdateParkingRentalRequest(
                        "B", "2", "B-200", new BigDecimal("500.00"), null)))
        .isInstanceOf(ParkingRentalException.class)
        .extracting("code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void update_byModerator_succeeds() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    UUID moderator = UUID.randomUUID();
    ParkingRentalView v =
        service.update(
            rentalId,
            moderator,
            true,
            new UpdateParkingRentalRequest("A", "-1", "045", new BigDecimal("350.00"), null));
    assertThat(v).isNotNull();
  }

  @Test
  void update_withStatusChange_appliesTransition() {
    ParkingRental r = sample();
    when(repo.findById(rentalId)).thenReturn(Optional.of(r));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    ParkingRentalView v =
        service.update(
            rentalId,
            author,
            false,
            new UpdateParkingRentalRequest(
                "A", "-1", "045", new BigDecimal("350.00"), ParkingRentalStatus.RENTED));
    assertThat(v.status()).isEqualTo(ParkingRentalStatus.RENTED);
  }

  @Test
  void getById_notFound_throws() {
    when(repo.findById(rentalId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(rentalId))
        .isInstanceOf(ParkingRentalException.class)
        .extracting("code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void view_whenPhoneInvalid_authorWhatsappIsNull() {
    when(repo.findById(rentalId)).thenReturn(Optional.of(sample()));
    when(normalizer.toEvolutionNumber(any()))
        .thenThrow(new br.com.condominio.feature.whatsapp.WhatsAppSendException("inválido"));
    ParkingRentalView v = service.getById(rentalId);
    assertThat(v.authorWhatsapp()).isNull();
    assertThat(v.authorPhone()).isEqualTo("11999990000");
  }
}
