package chaeso.zip.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private JwtTokenProvider jwtTokenProvider;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
        "test-secret-0123456789-0123456789-0123456789",
        Duration.ofMinutes(30),
        Duration.ofDays(14)));
    filter = new JwtAuthenticationFilter(jwtTokenProvider);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("유효한 access 토큰이 있으면 SecurityContext에 인증이 설정된다")
  void validToken_setsAuthentication() throws Exception {
    String token = jwtTokenProvider.createAccessToken(USER_ID);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    FilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    assertThat(principal).isInstanceOf(UserPrincipal.class);
    assertThat(((UserPrincipal) principal).userId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("헤더가 없으면 인증이 설정되지 않는다")
  void noHeader_noAuthentication() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("변조된 토큰이면 인증이 설정되지 않는다")
  void invalidToken_noAuthentication() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer tampered.token.value");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
