package br.com.condominio.feature.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominio.feature.user.dto.MyUnitView;
import br.com.condominio.shared.security.JwtAuthenticationConverter;
import br.com.condominio.shared.security.JwtService;
import br.com.condominio.shared.security.SecurityConfig;
import br.com.condominio.support.MockAuth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MyUnitsController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverter.class})
class MyUnitsControllerWebTest {

  private static final UUID UID = UUID.randomUUID();

  @Autowired private MockMvc mvc;
  @MockBean private UnitMemberService service;
  @MockBean private JwtService jwtService;

  @Test
  void myUnits_withResidentManage_returns200() throws Exception {
    when(service.listMyUnits(any())).thenReturn(List.of(new MyUnitView(UUID.randomUUID(), "702A")));
    mvc.perform(get("/api/units/me").with(MockAuth.user(UID, "RESIDENT_MANAGE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("702A"));
  }

  @Test
  void myUnits_withoutPermission_returns403() throws Exception {
    mvc.perform(get("/api/units/me").with(MockAuth.user(UID))).andExpect(status().isForbidden());
  }

  @Test
  void myUnits_unauthenticated_isRejected() throws Exception {
    mvc.perform(get("/api/units/me")).andExpect(status().is4xxClientError());
  }
}
