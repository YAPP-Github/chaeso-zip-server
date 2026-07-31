package chaeso.zip.server.auth.infrastructure.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.common.ratelimit.RateLimiter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigIntegrationTest.SecurityProbeController.class)
@Import({
    SecurityConfig.class,
    SecurityConfigIntegrationTest.TestBeans.class,
    SecurityConfigIntegrationTest.SecurityProbeController.class
})
class SecurityConfigIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private RateLimiter rateLimiter;

  @TestConfiguration
  static class TestBeans {
    @Bean
    CorsProperties corsProperties() {
      return new CorsProperties(List.of("http://localhost:3000"));
    }
  }

  /**
   * 실제 AuthController와 동일한 경로를 그대로 흉내내 SecurityConfig의 allow-list를 검증한다.
   * {@code @TestComponent}가 없으면 이 컨트롤러가 전체 앱 컴포넌트 스캔에 함께 걸려
   * 진짜 AuthController와 매핑이 충돌한다({@code @TestConfiguration}은 자동 제외되지만
   * {@code @RestController}는 아니다).
   */
  @TestComponent
  @RestController
  public static class SecurityProbeController {
    @GetMapping("/api/v1/security/protected")
    String protectedApi() {
      return "protected";
    }

    @PostMapping("/api/v1/auth/signup/email-code")
    String signupEmailCodeProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/non-public")
    String nonPublicAuthProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/login/methods")
    String loginMethodsProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/refresh")
    String refreshProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/logout")
    String logoutProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/google")
    String googleProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/google/link")
    String googleLinkProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/signup/google")
    String signupGoogleProbe() {
      return "ok";
    }
  }

  @Nested
  @DisplayName("보호 경로 인증")
  class ProtectedApi {

    @Test
    @DisplayName("보호 경로는 토큰 없이 접근하면 공통 JSON 401을 반환한다")
    void requiresAuthentication() throws Exception {
      mockMvc.perform(get("/api/v1/security/protected"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("C-004"));
    }

    @Test
    @DisplayName("유효한 Access Token이면 보호 경로에 접근할 수 있다")
    void validAccessToken_passes() throws Exception {
      UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
      given(jwtTokenProvider.parseAccess("valid-access-token"))
          .willReturn(new UserPrincipal(userId));

      mockMvc.perform(get("/api/v1/security/protected")
              .header("Authorization", "Bearer valid-access-token"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("auth 공개 경로")
  class PublicAuthPaths {

    @Test
    @DisplayName("회원가입 공개 경로는 토큰 없이 접근할 수 있다")
    void signup_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/signup/email-code"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용 목록에 없는 auth 경로는 토큰 없이 접근하면 401을 반환한다")
    void nonListed_requiresAuthentication() throws Exception {
      mockMvc.perform(post("/api/v1/auth/non-public"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("C-004"));
    }

    @Test
    @DisplayName("로그인 수단 조회는 토큰 없이 접근할 수 있다")
    void loginMethods_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/login/methods"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("재발급 경로는 Access Token 없이 접근할 수 있다")
    void refresh_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/refresh"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃 경로는 Access Token 없이 접근하면 401을 반환한다")
    void logout_requiresAuthentication() throws Exception {
      mockMvc.perform(post("/api/v1/auth/logout"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("C-004"));
    }

    @Test
    @DisplayName("구글 인증 진입 경로는 Access Token 없이 접근할 수 있다")
    void google_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/google"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("구글 연결 확인 경로는 Access Token 없이 접근할 수 있다")
    void googleLink_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/google/link"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("구글 최종 회원가입 경로는 Access Token 없이 접근할 수 있다")
    void signupGoogle_isPublic() throws Exception {
      mockMvc.perform(post("/api/v1/auth/signup/google"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("관리 엔드포인트는 메인 포트에서 공개되지 않는다")
  class ActuatorOnMainPort {

    @Test
    @DisplayName("메인 포트의 health 경로는 공개 경로로 허용하지 않는다")
    void health_requiresAuthentication() throws Exception {
      mockMvc.perform(get("/actuator/health"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("C-004"));
    }

    @Test
    @DisplayName("메인 포트의 prometheus 경로는 공개 경로로 허용하지 않는다")
    void prometheus_requiresAuthentication() throws Exception {
      mockMvc.perform(get("/actuator/prometheus"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("C-004"));
    }
  }

  @Nested
  @DisplayName("CORS")
  class Cors {

    @Test
    @DisplayName("허용된 Origin의 preflight 요청은 CORS 허용 헤더를 받는다")
    void allowedOrigin_receivesCorsHeader() throws Exception {
      mockMvc.perform(options("/api/v1/security/protected")
              .header("Origin", "http://localhost:3000")
              .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 preflight 요청은 CORS 오류로 거부된다")
    void disallowedOrigin_isRejected() throws Exception {
      mockMvc.perform(options("/api/v1/security/protected")
              .header("Origin", "http://evil.example.com")
              .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isForbidden());
    }
  }
}
