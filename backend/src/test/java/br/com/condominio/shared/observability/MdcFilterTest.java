package br.com.condominio.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcFilterTest {

  @Test
  void populatesRequestIdFromHeaderAndEchoesIt() throws ServletException, IOException {
    MdcFilter filter = new MdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    String[] capturedRequestIdHolder = new String[1];
    doAnswer(
            invocation -> {
              capturedRequestIdHolder[0] = MDC.get("requestId");
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    filter.doFilterInternal(request, response, chain);

    assertThat(capturedRequestIdHolder[0]).isEqualTo("abc-123");
    assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void generatesRequestIdWhenHeaderAbsent() throws ServletException, IOException {
    MdcFilter filter = new MdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    String[] capturedRequestIdHolder = new String[1];
    doAnswer(
            invocation -> {
              capturedRequestIdHolder[0] = MDC.get("requestId");
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    filter.doFilterInternal(request, response, chain);

    assertThat(capturedRequestIdHolder[0]).isNotBlank();
    assertThat(response.getHeader("X-Request-Id")).isEqualTo(capturedRequestIdHolder[0]);
  }
}
