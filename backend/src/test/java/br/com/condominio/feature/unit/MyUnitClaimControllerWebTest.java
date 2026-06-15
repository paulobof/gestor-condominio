package br.com.condominio.feature.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = MyUnitClaimController.class,
    properties = "app.feature.unitownership.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class MyUnitClaimControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private UnitOwnershipService service;
  @MockBean private JwtService jwtService;

  private MockMultipartFile proof() {
    return new MockMultipartFile(
        "proof", "comprovante.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
  }

  @Test
  void claim_authenticated_returns202() throws Exception {
    when(service.claimExtraUnit(eq(UID), eq("702C"), any())).thenReturn(UUID.randomUUID());
    mvc.perform(
            multipart("/api/auth/me/unit-claims")
                .file(proof())
                .param("unitCode", "702C")
                .with(MockAuth.user(UID)))
        .andExpect(status().isAccepted());
    verify(service).claimExtraUnit(eq(UID), eq("702C"), any());
  }

  @Test
  void claim_unauthenticated_isRejected() throws Exception {
    mvc.perform(multipart("/api/auth/me/unit-claims").file(proof()).param("unitCode", "702C"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void claim_unitHasMaster_returns409() throws Exception {
    when(service.claimExtraUnit(eq(UID), eq("702C"), any()))
        .thenThrow(new UnitOwnershipException("UNIT_HAS_MASTER", "já possui master"));
    mvc.perform(
            multipart("/api/auth/me/unit-claims")
                .file(proof())
                .param("unitCode", "702C")
                .with(MockAuth.user(UID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNIT_HAS_MASTER"));
  }
}
