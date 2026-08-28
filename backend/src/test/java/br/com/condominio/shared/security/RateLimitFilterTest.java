package br.com.condominio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  @Test
  void login_exceedingLimit_returns429() throws Exception {
    RateLimitProperties props = new RateLimitProperties();
    props.setLoginPerMinPerIp(2);
    RateLimitFilter filter =
        new RateLimitFilter(props, new ObjectMapper().registerModule(new JavaTimeModule()));

    for (int i = 0; i < 2; i++) {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
      req.setServletPath("/api/auth/login");
      req.setRemoteAddr("1.1.1.1");
      MockHttpServletResponse res = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();
      filter.doFilter(req, res, chain);
      assertThat(res.getStatus()).isNotEqualTo(429);
    }

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
    req.setServletPath("/api/auth/login");
    req.setRemoteAddr("1.1.1.1");
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(req, res, chain);
    assertThat(res.getStatus()).isEqualTo(429);
  }
}
