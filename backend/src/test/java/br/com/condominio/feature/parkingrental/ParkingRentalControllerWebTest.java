package br.com.condominio.feature.parkingrental;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.parkingrental.dto.ParkingRentalView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ParkingRentalController.class,
    properties = "app.feature.parkingrental.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class ParkingRentalControllerWebTest {

  private static final UUID UID = UUID.randomUUID();
  private static final UUID RID = UUID.randomUUID();
  private static final String MODERATE = "PARKING_RENTAL_MODERATE";

  @Autowired private MockMvc mvc;
  @MockBean private ParkingRentalService service;
  @MockBean private JwtService jwtService; // dependência do JwtAuthenticationConverter

  private ParkingRentalView view() {
    return new ParkingRentalView(
        RID,
        "A",
        "-1",
        "045",
        new BigDecimal("350.00"),
        ParkingRentalStatus.ACTIVE,
        UID,
        Instant.now(),
        "Ana Costa",
        "11999990000",
        "5511999990000");
  }

  @Test
  void list_authenticated_returns200() throws Exception {
    when(service.list(any(), any()))
        .thenReturn(new PageImpl<>(List.of(view()), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/parking-rentals").with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].spotNumber").value("045"))
        .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
  }

  @Test
  void list_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/parking-rentals")).andExpect(status().is4xxClientError());
    verifyNoInteractions(service);
  }

  @Test
  void get_returns200() throws Exception {
    when(service.getById(RID)).thenReturn(view());
    mvc.perform(get("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(RID.toString()))
        .andExpect(jsonPath("$.authorWhatsapp").value("5511999990000"));
  }

  @Test
  void create_returns201() throws Exception {
    when(service.create(eq(UID), any())).thenReturn(view());
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void create_blankTower_returns400() throws Exception {
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(service, never()).create(any(), any());
  }

  @Test
  void create_negativePrice_returns400() throws Exception {
    mvc.perform(
            post("/api/parking-rentals")
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":-5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void update_passesCanModerateFalse_whenNotModerator() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(false), any())).thenReturn(view());
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isOk());
    verify(service).update(eq(RID), eq(UID), eq(false), any());
  }

  @Test
  void update_passesCanModerateTrue_whenModerator() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(true), any())).thenReturn(view());
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID, MODERATE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isOk());
    verify(service).update(eq(RID), eq(UID), eq(true), any());
  }

  @Test
  void delete_returns204_andPassesCanModerate() throws Exception {
    mvc.perform(delete("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isNoContent());
    verify(service).delete(RID, UID, false);
  }

  @Test
  void notFound_mapsTo404() throws Exception {
    when(service.getById(RID)).thenThrow(new ParkingRentalException("NOT_FOUND", "não encontrado"));
    mvc.perform(get("/api/parking-rentals/{id}", RID).with(MockAuth.user(UID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forbidden_mapsTo403() throws Exception {
    when(service.update(eq(RID), eq(UID), eq(false), any()))
        .thenThrow(new ParkingRentalException("FORBIDDEN", "não é o autor"));
    mvc.perform(
            put("/api/parking-rentals/{id}", RID)
                .with(MockAuth.user(UID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tower\":\"A\",\"floor\":\"-1\",\"spotNumber\":\"045\",\"monthlyPrice\":350.00}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }
}
