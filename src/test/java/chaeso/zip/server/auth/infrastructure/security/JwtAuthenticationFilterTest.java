package chaeso.zip.server.auth.infrastructure.security;

import static chaeso.zip.server.auth.infrastructure.jwt.JwtTestFixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTestFixture;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private JwtTokenProvider jwtTokenProvider;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = JwtTestFixture.provider();
    filter = new JwtAuthenticationFilter(jwtTokenProvider);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("유효한 access 토큰이 있으면 SecurityContext에 사용자를 인증한다")
  void validTokenSetsAuthentication() throws Exception {
    String token = jwtTokenProvider.createAccessToken(USER_ID);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    FilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    Object principal = SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();
    assertThat(principal).isEqualTo(new UserPrincipal(USER_ID));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
      "Basic dXNlcjpwYXNz",
      "Bearer tampered.token.value",
      "Bearer "
  })
  @DisplayName("인증 헤더가 없거나 유효한 Bearer 토큰이 아니면 미인증 상태로 처리한다")
  void invalidAuthorizationHeaderKeepsAnonymous(String authorizationHeader) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (authorizationHeader != null) {
      request.addHeader("Authorization", authorizationHeader);
    }

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("인증된 이후 요청에서 변조된 토큰이 오면 이전 인증 정보를 지운다")
  void invalidTokenClearsPreviousAuthentication() throws Exception {
    String token = jwtTokenProvider.createAccessToken(USER_ID);
    MockHttpServletRequest validRequest = new MockHttpServletRequest();
    validRequest.addHeader("Authorization", "Bearer " + token);
    filter.doFilter(validRequest, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

    MockHttpServletRequest tamperedRequest = new MockHttpServletRequest();
    tamperedRequest.addHeader("Authorization", "Bearer tampered.token.value");
    filter.doFilter(tamperedRequest, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("토큰 검증 중 예상하지 못한 예외는 숨기지 않고 전파한다")
  void unexpectedTokenProviderExceptionPropagates() {
    JwtTokenProvider failingProvider = mock(JwtTokenProvider.class);
    given(failingProvider.parseAccess("valid-looking-token"))
        .willThrow(new IllegalStateException("JWT infrastructure failure"));
    JwtAuthenticationFilter failingFilter = new JwtAuthenticationFilter(failingProvider);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-looking-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    assertThatThrownBy(() -> failingFilter.doFilter(request, response, filterChain))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("JWT infrastructure failure");
  }
}
